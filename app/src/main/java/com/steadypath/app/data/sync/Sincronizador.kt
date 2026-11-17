package com.steadypath.app.data.sync

import android.util.Log
import com.google.gson.Gson
import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.dto.CreateOwnRoutineRequestDto
import com.steadypath.app.data.dto.CreateSessionRequestDto
import com.steadypath.app.data.dto.SaveOwnReferenceRequestDto
import com.steadypath.app.data.dto.UpdatePatientProfileRequestDto
import com.steadypath.app.data.local.ORDEN_LOCAL
import com.steadypath.app.data.local.RutinaDao
import com.steadypath.app.data.local.SesionDao
import com.steadypath.app.data.local.aEntidad
import com.steadypath.app.data.mapper.toDomain
import com.steadypath.app.data.session.SesionesPendientesStore
import com.steadypath.app.domain.model.EstadoDeSincronizacion
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.repository.SyncStatusRepository
import com.steadypath.app.domain.repository.UserSessionRepository
import dagger.Lazy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sube lo que quedó en [ColaDeOperaciones] cuando vuelve la red y mantiene el estado que la
 * interfaz le muestra a la persona ([SyncStatusRepository]).
 *
 * Disparadores: la red que vuelve (el [MonitorDeConectividad] también avisa al arrancar si
 * ya hay red, lo que cubre la apertura de la aplicación), cada guardado que sale bien
 * (prueba de que hay conexión), el inicio de sesión y [SincronizacionWorker] con la
 * aplicación cerrada. El [Mutex] evita dos envíos simultáneos de la misma operación; si
 * igual se repitiera (la respuesta se perdió después de que el servidor la guardó), el
 * backend reconoce sesiones y rutinas por el identificador que les puso el teléfono, y
 * referencia y perfil son reemplazos: mandarlos dos veces deja el mismo resultado.
 */
@Singleton
class Sincronizador @Inject constructor(
    private val cola: ColaDeOperaciones,
    private val usuario: UserSessionRepository,
    private val apiService: RehabApiService,
    private val caller: SafeApiCaller,
    private val monitor: MonitorDeConectividad,
    private val rutinaDao: RutinaDao,
    private val sesionDao: SesionDao,
    private val colaAnterior: SesionesPendientesStore,
    // Lazy: la precarga usa los repositorios, y los repositorios usan esta clase.
    private val precarga: Lazy<PrecargaOffline>,
    private val sesion: EstadoDeLaSesion,
    private val gson: Gson,
    dispatcher: CoroutineDispatcher
) : SyncStatusRepository {

    private val alcance = CoroutineScope(SupervisorJob() + dispatcher)
    private val envio = Mutex()
    private var iniciado = false

    private val rechazadas = MutableStateFlow(0)

    override val estado: Flow<EstadoDeSincronizacion> =
        combine(monitor.enLinea, cola.cantidad(), rechazadas, sesion.vencida) {
                enLinea, pendientes, rechazadas, vencida ->
            EstadoDeSincronizacion(enLinea, pendientes, rechazadas, vencida)
        }

    override fun avisoDeRechazoVisto() {
        rechazadas.value = 0
    }

    fun iniciar() {
        if (iniciado) return
        iniciado = true
        alcance.launch {
            rescatarColaAnterior()
            monitor.enLinea.collect { hayRed ->
                if (hayRed) {
                    runCatching { enviarPendientes() }
                        .onFailure { Log.w(TAG, "Falló el envío al volver la red", it) }
                    precarga.get().ejecutar()
                }
            }
        }
    }

    fun enviarEnSegundoPlano() {
        alcance.launch { enviarPendientes() }
    }

    /** Quien entra puede traer cosas sin subir de antes de que se le venciera la sesión. */
    fun alIniciarSesion() {
        sesion.vigente()
        alcance.launch {
            enviarPendientes()
            precarga.get().ejecutar(forzar = true)
        }
    }

    /** @return si quedó algo en la cola sin poder enviarse. */
    suspend fun enviarPendientes(): Boolean = envio.withLock {
        val idUsuario = usuario.get()?.idUsuario ?: return false
        val pendientes = cola.deUsuario(idUsuario)
        if (pendientes.isEmpty()) return false

        // Rutinas que el servidor guardó con otro identificador durante este envío.
        val rutinasReasignadas = mutableMapOf<String, String>()
        val resumen = EnvioDePendientes.procesar(
            pendientes,
            enviar = { operacion -> enviar(operacion, rutinasReasignadas) },
            quitar = { id -> cola.quitar(id) },
            alDescartar = { operacion, error -> deshacer(operacion, error) }
        )
        Log.i(
            TAG,
            "Pendientes: ${pendientes.size}, enviadas: ${resumen.enviadas}, " +
                "descartadas por el servidor: ${resumen.descartadas}"
        )
        if (resumen.descartadas > 0) rechazadas.update { it + resumen.descartadas }
        // Lo que subió cambia los totales que calcula el servidor (progreso, cumplimiento).
        if (resumen.enviadas > 0) alcance.launch { precarga.get().ejecutar(forzar = true) }
        resumen.quedanPendientes
    }

    private suspend fun enviar(
        operacion: OperacionPendiente,
        rutinasReasignadas: MutableMap<String, String>
    ): Result<*> = try {
        when (operacion.tipo) {
            TipoDeOperacion.SESION -> {
                val guardada = leer<CreateSessionRequestDto>(operacion)
                val idReasignado = guardada.idRutina?.let { rutinasReasignadas[it] }
                val solicitud = if (idReasignado != null) guardada.copy(idRutina = idReasignado) else guardada
                caller.execute({ apiService.saveSession(solicitud) }) { }
                    .onSuccess { sesionDao.marcarSincronizada(operacion.destino) }
            }

            TipoDeOperacion.RUTINA_PROPIA -> {
                val solicitud = leer<CreateOwnRoutineRequestDto>(operacion)
                caller.execute({ apiService.createOwnRoutine(solicitud) }) { it.toDomain() }
                    .onSuccess { rutina -> alSubirRutina(operacion, rutina, rutinasReasignadas) }
            }

            TipoDeOperacion.REFERENCIA_PROPIA -> {
                val solicitud = leer<SaveOwnReferenceRequestDto>(operacion)
                caller.execute({ apiService.saveOwnReference(solicitud) }) { }
            }

            TipoDeOperacion.PERFIL_PACIENTE -> {
                val solicitud = leer<UpdatePatientProfileRequestDto>(operacion)
                caller.execute({ apiService.updatePatientProfile(solicitud) }) { it.toDomain() }
                    .onSuccess { usuario.save(it) }
            }
        }
    } catch (e: CancellationException) {
        // Un envío interrumpido (el sistema frenó el worker) no es un rechazo: si se
        // tratara como tal, la operación se borraría de la cola sin haberse enviado.
        throw e
    } catch (e: Exception) {
        // Una carga que no se puede leer no va a mejorar reintentando: rechazo definitivo.
        Result.failure<Unit>(e)
    }

    private inline fun <reified T> leer(operacion: OperacionPendiente): T =
        requireNotNull(gson.fromJson(operacion.cargaJson, T::class.java)) {
            "Operación ${operacion.tipo} sin carga"
        }

    /**
     * La versión del servidor reemplaza a la armada en el teléfono. Con el backend al día
     * conserva el identificador; uno anterior al cambio le pone el suyo, y entonces hay que
     * corregir lo que apuntaba al identificador local: sin eso las sesiones hechas con esa
     * rutina llegarían citando una rutina que el servidor no conoce y las rechazaría.
     */
    private suspend fun alSubirRutina(
        operacion: OperacionPendiente,
        rutina: Routine,
        rutinasReasignadas: MutableMap<String, String>
    ) {
        val idLocal = operacion.destino
        rutinaDao.guardar(rutina.aEntidad(pendienteDeSubir = false, orden = ORDEN_LOCAL))
        if (rutina.idRutina.equals(idLocal, ignoreCase = true)) return

        rutinasReasignadas[idLocal] = rutina.idRutina
        rutinaDao.borrar(idLocal)
        sesionDao.reasignarRutina(idLocal, rutina.idRutina)
        // También en la cola: si este envío se corta, el próximo ya no tiene el mapa.
        cola.deUsuario(operacion.idUsuario)
            .filter { it.tipo == TipoDeOperacion.SESION }
            .forEach { sesion ->
                reasignarRutinaDeSesion(sesion.cargaJson, idLocal, rutina.idRutina, gson)
                    ?.let { cola.actualizarCarga(sesion.id, it) }
            }
    }

    /** El teléfono había dado la operación por hecha; si el servidor la rechaza, deja de mostrarla. */
    private suspend fun deshacer(operacion: OperacionPendiente, error: Throwable) {
        Log.w(TAG, "El servidor rechazó ${operacion.tipo} (${operacion.destino}): ${error.message}")
        when (operacion.tipo) {
            TipoDeOperacion.SESION -> sesionDao.borrar(operacion.destino)
            TipoDeOperacion.RUTINA_PROPIA -> rutinaDao.borrar(operacion.destino)
            // Referencia y perfil se corrigen solos con la próxima lectura del servidor.
            TipoDeOperacion.REFERENCIA_PROPIA, TipoDeOperacion.PERFIL_PACIENTE -> Unit
        }
    }

    private suspend fun rescatarColaAnterior() {
        runCatching {
            colaAnterior.extraerTodas().forEach { sesion ->
                cola.agregar(
                    OperacionPendiente(
                        id = sesion.idCliente,
                        idUsuario = sesion.idUsuario,
                        tipo = TipoDeOperacion.SESION,
                        destino = sesion.idCliente,
                        cargaJson = gson.toJson(sesion.solicitud)
                    )
                )
            }
        }.onFailure { Log.w(TAG, "No se pudo rescatar la cola de sesiones anterior", it) }
    }

    private companion object {
        const val TAG = "Sincronizador"
    }
}

/**
 * Carga de una sesión encolada con la rutina [idAnterior] cambiada por [idNuevo], o `null`
 * si esa sesión no es de esa rutina (o no se puede leer) y no hay nada que cambiar.
 */
internal fun reasignarRutinaDeSesion(
    cargaJson: String,
    idAnterior: String,
    idNuevo: String,
    gson: Gson
): String? {
    val solicitud = runCatching {
        gson.fromJson(cargaJson, CreateSessionRequestDto::class.java)
    }.getOrNull() ?: return null
    if (!solicitud.idRutina.equals(idAnterior, ignoreCase = true)) return null
    return gson.toJson(solicitud.copy(idRutina = idNuevo))
}
