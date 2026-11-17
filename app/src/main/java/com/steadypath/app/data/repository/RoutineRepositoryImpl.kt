package com.steadypath.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.dto.CreateOwnRoutineRequestDto
import com.steadypath.app.data.dto.CreateRoutineExerciseDto
import com.steadypath.app.data.dto.EditRoutineRequestDto
import com.steadypath.app.data.dto.RoutineComplianceDto
import com.steadypath.app.data.dto.RoutineStateRequestDto
import com.steadypath.app.data.local.EjercicioDao
import com.steadypath.app.data.local.LecturaConCache
import com.steadypath.app.data.local.ORDEN_LOCAL
import com.steadypath.app.data.local.RutinaDao
import com.steadypath.app.data.local.aDominio
import com.steadypath.app.data.local.aEntidad
import com.steadypath.app.data.mapper.toDomain
import com.steadypath.app.data.sync.ColaDeOperaciones
import com.steadypath.app.data.sync.EnvioDePendientes
import com.steadypath.app.data.sync.OperacionPendiente
import com.steadypath.app.data.sync.TipoDeOperacion
import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.RoutineCompliance
import com.steadypath.app.domain.model.RoutineExercise
import com.steadypath.app.domain.repository.RoutineExerciseInput
import com.steadypath.app.domain.repository.RoutineRepository
import com.steadypath.app.domain.repository.UserSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class RoutineRepositoryImpl @Inject constructor(
    private val apiService: RehabApiService,
    private val caller: SafeApiCaller,
    private val lectura: LecturaConCache,
    private val rutinaDao: RutinaDao,
    private val ejercicioDao: EjercicioDao,
    private val cola: ColaDeOperaciones,
    private val usuario: UserSessionRepository,
    private val gson: Gson
) : RoutineRepository {

    /**
     * Las rutinas viven en su tabla y no como una respuesta guardada: así las creadas sin
     * red aparecen en el listado y [getRoutine] se resuelve con lo que trajo el listado,
     * sin haber abierto antes cada rutina por separado.
     */
    override fun getPatientRoutines(
        idPaciente: String,
        soloActivas: Boolean
    ): Flow<Result<List<Routine>>> = lectura.flujo(
        leer = { leerDePaciente(idPaciente, soloActivas) },
        llamar = {
            caller.execute({ apiService.getPatientRoutines(idPaciente, soloActivas) }) { dtos ->
                dtos.map { it.toDomain() }
            }
        },
        guardar = { rutinas ->
            rutinaDao.reemplazarDePaciente(
                idPaciente,
                soloActivas,
                rutinas.mapIndexed { indice, rutina ->
                    // La respuesta no siempre repite el paciente: se usa el que se pidió.
                    rutina.copy(idPaciente = idPaciente).aEntidad(pendienteDeSubir = false, orden = indice)
                }
            )
            lectura.marcar(claveDeListado(idPaciente, soloActivas))
        },
        borrar = {
            rutinaDao.borrarDePaciente(idPaciente)
            lectura.olvidar(idPaciente)
        },
        // Se relee la tabla: a lo del servidor se le suma lo creado sin red.
        aDominio = { rutinas -> leerDePaciente(idPaciente, soloActivas) ?: rutinas }
    )

    private suspend fun leerDePaciente(idPaciente: String, soloActivas: Boolean): List<Routine>? {
        // El listado completo también sirve para responder por las activas, no al revés.
        val sincronizado = lectura.estaMarcada(claveDeListado(idPaciente, soloActivas = false)) ||
            (soloActivas && lectura.estaMarcada(claveDeListado(idPaciente, soloActivas = true)))
        val rutinas = rutinaDao.dePaciente(idPaciente)
            .filter { !soloActivas || it.rutina.activa }
            .map { it.aDominio() }
        // Sin marca, una tabla vacía es "nunca se pidió"; con algo creado sin red hay qué mostrar.
        return if (sincronizado || rutinas.isNotEmpty()) rutinas else null
    }

    override fun getRoutine(idRutina: String): Flow<Result<Routine>> = flow {
        val local = rutinaDao.porId(idRutina)
        if (local?.rutina?.pendienteDeSubir == true) {
            // El servidor todavía no la conoce: preguntarle daría un 404 que borraría la copia.
            emit(Result.success(local.aDominio()))
            return@flow
        }
        emitAll(
            lectura.flujo(
                leer = { rutinaDao.porId(idRutina)?.aDominio() },
                llamar = { caller.execute({ apiService.getRoutine(idRutina) }) { it.toDomain() } },
                guardar = { rutina -> guardarSincronizada(rutina) },
                borrar = { rutinaDao.borrar(idRutina) },
                aDominio = { it }
            )
        )
    }

    /**
     * Sin red la rutina se arma en el teléfono con el identificador que después va a tener
     * en el servidor y queda en la cola de salida. Se devuelve como creada: la persona
     * puede usarla enseguida, que es para lo que la armó.
     */
    override fun createOwnRoutine(
        nombreRutina: String,
        diasAsignados: List<DiaSemana>,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<Routine>> = flow {
        val idCliente = UUID.randomUUID().toString()
        val solicitud = CreateOwnRoutineRequestDto(
            idCliente = idCliente,
            nombreRutina = nombreRutina,
            diasAsignados = diasAsignados.map { it.ordinal },
            ejercicios = ejercicios.toDtos()
        )
        val resultado = caller.execute({ apiService.createOwnRoutine(solicitud) }) { it.toDomain() }
        val error = resultado.exceptionOrNull()
        if (error == null) {
            resultado.getOrNull()?.let { guardarSincronizada(it) }
            emit(resultado)
            return@flow
        }
        val idUsuario = usuario.get()?.idUsuario
        if (!EnvioDePendientes.esReintentable(error) || idUsuario == null) {
            emit(resultado)
            return@flow
        }

        val catalogo = ejercicioDao.porIds(ejercicios.map { it.idEjercicio })
            .associateBy { it.idEjercicio.lowercase() }
        val local = Routine(
            idRutina = idCliente,
            idPaciente = idUsuario,
            idProfesionalAsignador = null,
            nombreRutina = nombreRutina,
            fechaCreacion = Instant.now().toString(),
            activa = true,
            diasAsignados = diasAsignados,
            ejercicios = ejercicios.map { pedido ->
                val ejercicio = catalogo[pedido.idEjercicio.lowercase()]?.aDominio()
                RoutineExercise(
                    idRutinaEjercicio = UUID.randomUUID().toString(),
                    idEjercicio = pedido.idEjercicio,
                    nombreEjercicio = ejercicio?.nombre.orEmpty(),
                    repeticionesObjetivo = pedido.repeticionesObjetivo,
                    series = pedido.series,
                    duracionMinutos = ejercicio?.duracionMinutos ?: 0,
                    zonasAfectadas = ejercicio?.zonasAfectadas.orEmpty(),
                    nivelDificultad = ejercicio?.nivelDificultad.orEmpty()
                )
            }
        )
        rutinaDao.guardar(local.aEntidad(pendienteDeSubir = true, orden = ORDEN_LOCAL))
        cola.agregar(
            OperacionPendiente(
                id = idCliente,
                idUsuario = idUsuario,
                tipo = TipoDeOperacion.RUTINA_PROPIA,
                destino = idCliente,
                cargaJson = gson.toJson(solicitud)
            )
        )
        emit(Result.success(local))
    }

    override fun getRoutineCompliance(idRutina: String): Flow<Result<RoutineCompliance>> =
        lectura.instantanea(
            clave = "cumplimientoRutina:$idRutina",
            tipo = RoutineComplianceDto::class.java,
            call = { apiService.getRoutineCompliance(idRutina) },
            map = { it.toDomain() }
        )

    override fun getPatientRoutineCompliance(
        idPaciente: String,
        soloActivas: Boolean
    ): Flow<Result<List<RoutineCompliance>>> = lectura.instantanea(
        clave = "cumplimientoRutinas:$idPaciente:$soloActivas",
        tipo = object : TypeToken<List<RoutineComplianceDto>>() {}.type,
        call = { apiService.getPatientRoutineCompliance(idPaciente, soloActivas) },
        map = { dtos -> dtos.map { it.toDomain() } }
    )

    override fun setRoutineActive(idRutina: String, activa: Boolean): Flow<Result<Routine>> = flow {
        emit(
            caller.execute({ apiService.setRoutineState(idRutina, RoutineStateRequestDto(activa)) }) {
                it.toDomain()
            }.onSuccess { guardarSincronizada(it) }
        )
    }

    override fun editRoutineExercises(
        idRutina: String,
        diasAsignados: List<DiaSemana>,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<Routine>> = flow {
        val solicitud = EditRoutineRequestDto(
            diasAsignados = diasAsignados.map { it.ordinal },
            ejercicios = ejercicios.toDtos()
        )
        emit(
            caller.execute({ apiService.editRoutineExercises(idRutina, solicitud) }) { it.toDomain() }
                .onSuccess { guardarSincronizada(it) }
        )
    }

    /** Conserva la posición que la rutina ya tenía en el listado. */
    private suspend fun guardarSincronizada(rutina: Routine) {
        val anterior = rutinaDao.porId(rutina.idRutina)?.rutina
        rutinaDao.guardar(
            rutina
                .copy(idPaciente = rutina.idPaciente.ifEmpty { anterior?.idPaciente.orEmpty() })
                .aEntidad(pendienteDeSubir = false, orden = anterior?.orden ?: ORDEN_LOCAL)
        )
    }

    private fun List<RoutineExerciseInput>.toDtos(): List<CreateRoutineExerciseDto> = map {
        CreateRoutineExerciseDto(
            idEjercicio = it.idEjercicio,
            repeticionesObjetivo = it.repeticionesObjetivo,
            series = it.series
        )
    }

    private companion object {
        fun claveDeListado(idPaciente: String, soloActivas: Boolean) =
            "marca:rutinas:$idPaciente:${if (soloActivas) "activas" else "todas"}"
    }
}
