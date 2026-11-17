package com.steadypath.app.presentation.session

import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.RoutineExercise
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estado en memoria de la rutina en curso: secuencia de ejercicios y resultados
 * medidos (reps, tiempo, EVA) que viajan en un único POST al finalizar.
 * Inactivo = flujo de ejercicio individual (comportamiento clásico).
 */
@Singleton
class RoutineSessionTracker @Inject constructor() {

    /** Resultado medido de un ejercicio completado, a la espera del envío. */
    data class PendingResult(
        val idEjercicio: String,
        val repeticiones: Int,
        val duracionSegundos: Int,
        val escalaEVA: Int? = null
    )

    private var exercises: List<RoutineExercise> = emptyList()
    private var currentIndex = 0
    private val results = mutableListOf<PendingResult>()
    private var routineId: String? = null
    private var startedAt: Instant? = null

    val isActive: Boolean
        get() = exercises.isNotEmpty()

    /** null = práctica libre (sin rutina asociada). */
    val currentRoutineId: String?
        get() = routineId

    /** Inicio de la sesión en ISO-8601 UTC, para fechaHoraInicio del backend. */
    val startedAtIso: String?
        get() = startedAt?.toString()

    val pendingResults: List<PendingResult>
        get() = results.toList()

    private val totalSeconds: Int
        get() = results.sumOf { it.duracionSegundos }

    /** Duración total medida en minutos; el backend exige mínimo 0.01. */
    val totalDurationMinutes: Double
        get() = (totalSeconds / 60.0).coerceAtLeast(0.01)

    /** Minutos para mostrar en el resumen: techo, mínimo 1. */
    val totalMinutes: Int
        get() = ((totalSeconds + 59) / 60).coerceAtLeast(1)

    fun start(routine: Routine, fromIdEjercicio: String? = null) {
        exercises = routine.ejercicios
        // Si se entra por un ejercicio intermedio, la secuencia sigue desde ahí.
        currentIndex = exercises.indexOfFirst { it.idEjercicio == fromIdEjercicio }
            .coerceAtLeast(0)
        routineId = routine.idRutina
        results.clear()
        startedAt = Instant.now()
    }

    /** true si el ejercicio es el paso actual de la rutina en curso. */
    fun isCurrent(idEjercicio: String): Boolean =
        exercises.getOrNull(currentIndex)?.idEjercicio == idEjercicio

    /** Ejercicio del paso actual (series/reps prescriptas), o null sin rutina en curso. */
    fun currentExercise(): RoutineExercise? = exercises.getOrNull(currentIndex)

    /** Registra reps y tiempo medidos del ejercicio recién completado. */
    fun recordExerciseResult(idEjercicio: String, repeticiones: Int, duracionSegundos: Int) {
        // Ejercicio suelto: la sesión arranca retroactivamente con su primer resultado.
        if (startedAt == null) startedAt = Instant.now().minusSeconds(duracionSegundos.toLong())
        results += PendingResult(idEjercicio, repeticiones, duracionSegundos)
    }

    /** Adjunta la escala EVA reportada al último resultado registrado. */
    fun attachEva(escalaEVA: Int?) {
        val last = results.lastOrNull() ?: return
        results[results.lastIndex] = last.copy(escalaEVA = escalaEVA)
    }

    /** Avanza al siguiente ejercicio; null si la rutina terminó. */
    fun advance(): RoutineExercise? {
        currentIndex++
        return exercises.getOrNull(currentIndex)
    }

    fun clear() {
        exercises = emptyList()
        currentIndex = 0
        results.clear()
        routineId = null
        startedAt = null
    }
}
