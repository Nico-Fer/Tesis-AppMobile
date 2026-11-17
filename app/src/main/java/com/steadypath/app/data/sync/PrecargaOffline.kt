package com.steadypath.app.data.sync

import android.util.Log
import com.steadypath.app.data.comparison.RemoteFirstReferenceRepository
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.UserRole
import com.steadypath.app.domain.repository.ExerciseRepository
import com.steadypath.app.domain.repository.PatientToleranceRepository
import com.steadypath.app.domain.repository.RoutineRepository
import com.steadypath.app.domain.repository.SessionRepository
import com.steadypath.app.domain.repository.UserSessionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.OffsetDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Baja por adelantado lo que el paciente necesita para ejercitarse sin red.
 *
 * Los repositorios guardan lo que se les pide, pero solo lo que se les pide: sin esto, un
 * ejercicio que la persona nunca abrió con conexión no tendría ni referencia ni tolerancia
 * en el teléfono, y justo ese día sin señal la aplicación le pediría grabar una referencia
 * que ya tiene en el servidor.
 *
 * Recorre las mismas lecturas que hacen las pantallas (así se guardan por el mismo camino)
 * y, por cada ejercicio de las rutinas activas, la referencia de movimiento y la
 * tolerancia. El profesional no precarga: consulta lo que ya abrió.
 */
@Singleton
class PrecargaOffline @Inject constructor(
    private val usuario: UserSessionRepository,
    private val ejercicios: ExerciseRepository,
    private val rutinas: RoutineRepository,
    private val sesiones: SessionRepository,
    private val referencias: RemoteFirstReferenceRepository,
    private val tolerancias: PatientToleranceRepository,
    private val monitor: MonitorDeConectividad
) {
    private val mutex = Mutex()
    private var ultimaVez = 0L
    private var ultimoUsuario: String? = null

    /**
     * @param forzar saltea el intervalo mínimo: hubo un cambio que deja vieja la copia
     *   (inicio de sesión, operaciones recién subidas).
     */
    suspend fun ejecutar(forzar: Boolean = false) {
        if (!monitor.hayRed()) return
        try {
            mutex.withLock { precargar(forzar) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Es una mejora, no un requisito: si falla, la aplicación sigue como estaba.
            Log.w(TAG, "No se pudo completar la precarga", e)
        }
    }

    private suspend fun precargar(forzar: Boolean) {
        val actual = usuario.get() ?: return
        if (actual.rol != UserRole.PACIENTE) return
        val ahora = System.currentTimeMillis()
        val mismoUsuario = ultimoUsuario == actual.idUsuario
        if (!forzar && mismoUsuario && ahora - ultimaVez < INTERVALO_MINIMO_MS) return

        val idPaciente = actual.idUsuario
        ejercicios.getExercises().collect()
        var activas: List<Routine> = emptyList()
        var respondio = false
        rutinas.getPatientRoutines(idPaciente, soloActivas = true).collect { resultado ->
            resultado.onSuccess { activas = it; respondio = true }
        }
        // Sin rutinas no hay de qué bajar referencias, y no se da por hecha: se reintenta
        // en la próxima oportunidad en vez de esperar el intervalo mínimo.
        if (!respondio) return
        sesiones.getHistory(idPaciente).collect()
        sesiones.getCompliance(idPaciente).collect()
        sesiones.getProgressReport(idPaciente, desfaseHorarioEnMinutos()).collect()

        val idsDeEjercicios = activas.flatMap { it.ejercicios }.map { it.idEjercicio }.distinct()
        referencias.precargar(idsDeEjercicios)
        idsDeEjercicios.forEach { tolerancias.getOwnTolerance(it) }

        ultimaVez = ahora
        ultimoUsuario = idPaciente
        Log.i(TAG, "Precarga lista: ${activas.size} rutinas, ${idsDeEjercicios.size} ejercicios")
    }

    private fun desfaseHorarioEnMinutos(): Int = OffsetDateTime.now().offset.totalSeconds / 60

    private companion object {
        const val TAG = "PrecargaOffline"

        /** La red puede ir y venir varias veces por minuto; no hace falta rebajar todo cada vez. */
        const val INTERVALO_MINIMO_MS = 10 * 60 * 1000L
    }
}
