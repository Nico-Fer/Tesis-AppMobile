package com.steadypath.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.dto.ComplianceDto
import com.steadypath.app.data.dto.CreateSessionRequestDto
import com.steadypath.app.data.dto.EvaRecordDto
import com.steadypath.app.data.dto.ProgressReportDto
import com.steadypath.app.data.dto.SessionResultRequestDto
import com.steadypath.app.data.local.EjercicioDao
import com.steadypath.app.data.local.LecturaConCache
import com.steadypath.app.data.local.RutinaDao
import com.steadypath.app.data.local.SesionDao
import com.steadypath.app.data.local.aDominio
import com.steadypath.app.data.local.aEntidad
import com.steadypath.app.data.mapper.toDomain
import com.steadypath.app.data.sync.ColaDeOperaciones
import com.steadypath.app.data.sync.EnvioDePendientes
import com.steadypath.app.data.sync.OperacionPendiente
import com.steadypath.app.data.sync.Sincronizador
import com.steadypath.app.data.sync.TipoDeOperacion
import com.steadypath.app.domain.model.Compliance
import com.steadypath.app.domain.model.PainRecord
import com.steadypath.app.domain.model.ProgressReport
import com.steadypath.app.domain.model.Session
import com.steadypath.app.domain.model.SessionResult
import com.steadypath.app.domain.model.SesionEnEsperaException
import com.steadypath.app.domain.repository.SessionRepository
import com.steadypath.app.domain.repository.SessionResultInput
import com.steadypath.app.domain.repository.UserSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class SessionRepositoryImpl @Inject constructor(
    private val apiService: RehabApiService,
    private val caller: SafeApiCaller,
    private val lectura: LecturaConCache,
    private val sesionDao: SesionDao,
    private val rutinaDao: RutinaDao,
    private val ejercicioDao: EjercicioDao,
    private val cola: ColaDeOperaciones,
    private val usuario: UserSessionRepository,
    private val sincronizador: Sincronizador,
    private val gson: Gson
) : SessionRepository {

    /**
     * Una sesión terminada no se pierde por falta de red (RNF-10): si el envío falla por
     * un motivo transitorio, queda en la cola de salida y se informa con
     * [SesionEnEsperaException]. Un rechazo definitivo del servidor se devuelve tal cual.
     *
     * En los dos casos la sesión se guarda también en el historial local, para que el
     * inicio la cuente como hecha sin esperar a que el servidor la confirme.
     */
    override fun saveSession(
        idRutina: String?,
        fechaHoraInicio: String?,
        duracionMinutos: Double,
        estado: String,
        resultados: List<SessionResultInput>
    ): Flow<Result<Session>> = flow {
        val idCliente = UUID.randomUUID().toString()
        val solicitud = CreateSessionRequestDto(
            idCliente = idCliente,
            idRutina = idRutina,
            fechaHoraInicio = fechaHoraInicio,
            duracionMinutos = duracionMinutos,
            estado = estado,
            resultados = resultados.map {
                SessionResultRequestDto(
                    idEjercicio = it.idEjercicio,
                    repeticionesRealizadas = it.repeticionesRealizadas,
                    repeticionesCorrectas = it.repeticionesCorrectas,
                    porcentajeCorrectitud = it.porcentajeCorrectitud,
                    escalaEVA = it.escalaEVA
                )
            }
        )
        val idUsuario = usuario.get()?.idUsuario
        val resultado = caller.execute({ apiService.saveSession(solicitud) }) { it.toDomain() }
        val error = resultado.exceptionOrNull()
        if (error == null) {
            if (idUsuario != null) guardarEnHistorial(solicitud, idUsuario, pendienteDeSubir = false)
            // Hay red: es buen momento para mandar lo que haya quedado de antes.
            sincronizador.enviarEnSegundoPlano()
            emit(resultado)
            return@flow
        }
        if (EnvioDePendientes.esReintentable(error) && idUsuario != null) {
            guardarEnHistorial(solicitud, idUsuario, pendienteDeSubir = true)
            cola.agregar(
                OperacionPendiente(
                    id = idCliente,
                    idUsuario = idUsuario,
                    tipo = TipoDeOperacion.SESION,
                    destino = idCliente,
                    cargaJson = gson.toJson(solicitud)
                )
            )
            emit(Result.failure(SesionEnEsperaException()))
        } else {
            emit(resultado)
        }
    }

    /**
     * Arma la sesión como la devolvería el historial del servidor, con los nombres que la
     * respuesta del guardado no trae, sacados de la rutina y el catálogo locales. El
     * backend usa el `idCliente` como identificador de la sesión, así que esta fila es la
     * misma que después va a llegar en el historial.
     */
    private suspend fun guardarEnHistorial(
        solicitud: CreateSessionRequestDto,
        idUsuario: String,
        pendienteDeSubir: Boolean
    ) {
        runCatching {
            val idSesion = solicitud.idCliente ?: return
            val nombres = ejercicioDao.porIds(solicitud.resultados.map { it.idEjercicio })
                .associate { it.idEjercicio.lowercase() to it.nombre }
            val sesion = Session(
                idSesion = idSesion,
                idPaciente = idUsuario,
                idRutina = solicitud.idRutina,
                nombreRutina = solicitud.idRutina?.let { rutinaDao.porId(it)?.rutina?.nombreRutina },
                fechaHoraInicio = solicitud.fechaHoraInicio ?: Instant.now().toString(),
                duracionMinutos = solicitud.duracionMinutos,
                estado = solicitud.estado,
                resultados = solicitud.resultados.map {
                    SessionResult(
                        idResultado = UUID.randomUUID().toString(),
                        idEjercicio = it.idEjercicio,
                        nombreEjercicio = nombres[it.idEjercicio.lowercase()],
                        repeticionesRealizadas = it.repeticionesRealizadas,
                        repeticionesCorrectas = it.repeticionesCorrectas,
                        porcentajeCorrectitud = it.porcentajeCorrectitud,
                        escalaEVA = it.escalaEVA
                    )
                }
            )
            // Recién hecha: va antes que lo que el servidor ya había listado (más nuevo primero).
            sesionDao.guardar(sesion.aEntidad(pendienteDeSubir, orden = ORDEN_RECIEN_HECHA))
        }
    }

    /**
     * El historial tiene tabla propia para poder sumarle lo hecho sin red: el inicio decide
     * con él qué rutinas de hoy ya están cumplidas.
     */
    override fun getHistory(idPaciente: String): Flow<Result<List<Session>>> = lectura.flujo(
        leer = { leerHistorial(idPaciente) },
        llamar = {
            caller.execute({ apiService.getSessionHistory(idPaciente) }) { dtos -> dtos.map { it.toDomain() } }
        },
        guardar = { sesiones ->
            sesionDao.reemplazarDePaciente(
                idPaciente,
                sesiones.mapIndexed { indice, sesion ->
                    sesion.copy(idPaciente = idPaciente).aEntidad(pendienteDeSubir = false, orden = indice)
                }
            )
            lectura.marcar(claveDeHistorial(idPaciente))
        },
        borrar = {
            sesionDao.borrarDePaciente(idPaciente)
            lectura.olvidar(idPaciente)
        },
        aDominio = { sesiones -> leerHistorial(idPaciente) ?: sesiones }
    )

    private suspend fun leerHistorial(idPaciente: String): List<Session>? {
        val sesiones = sesionDao.dePaciente(idPaciente).map { it.aDominio() }
        val sincronizado = lectura.estaMarcada(claveDeHistorial(idPaciente))
        return if (sincronizado || sesiones.isNotEmpty()) sesiones else null
    }

    // Lo que sigue lo calcula el servidor y se guarda como llegó: sin red muestra el último
    // valor conocido, que no incluye las sesiones todavía sin subir.

    override fun getEvaHistory(idPaciente: String): Flow<Result<List<PainRecord>>> =
        lectura.instantanea(
            clave = "eva:$idPaciente",
            tipo = object : TypeToken<List<EvaRecordDto>>() {}.type,
            call = { apiService.getEvaHistory(idPaciente) },
            map = { dtos -> dtos.map { it.toDomain() } }
        )

    override fun getCompliance(idPaciente: String): Flow<Result<Compliance>> = lectura.instantanea(
        clave = "cumplimiento:$idPaciente",
        tipo = ComplianceDto::class.java,
        call = { apiService.getCompliance(idPaciente) },
        map = { it.toDomain() }
    )

    override fun getProgressReport(
        idPaciente: String,
        tzOffsetMinutes: Int
    ): Flow<Result<ProgressReport>> = lectura.instantanea(
        clave = "progreso:$idPaciente",
        tipo = ProgressReportDto::class.java,
        call = { apiService.getProgressReport(idPaciente, tzOffsetMinutes) },
        map = { it.toDomain() }
    )

    private companion object {
        const val ORDEN_RECIEN_HECHA = -1

        fun claveDeHistorial(idPaciente: String) = "marca:historial:$idPaciente"
    }
}
