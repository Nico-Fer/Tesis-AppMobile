package com.steadypath.app.presentation.session

import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.RoutineExercise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutineSessionTrackerTest {

    private val tracker = RoutineSessionTracker()

    private fun exercise(id: String) = RoutineExercise(
        idRutinaEjercicio = "re-$id",
        idEjercicio = id,
        nombreEjercicio = "Ejercicio $id",
        repeticionesObjetivo = 10,
        series = 3
    )

    private fun routine(vararg ids: String) = Routine(
        idRutina = "rutina-1",
        idPaciente = "paciente-1",
        idProfesionalAsignador = null,
        nombreRutina = "Rutina test",
        fechaCreacion = "2026-01-01",
        activa = true,
        diasAsignados = emptyList(),
        ejercicios = ids.map { exercise(it) }
    )

    @Test
    fun `acumula resultados y duracion total de varios ejercicios`() {
        tracker.start(routine("a", "b"))

        tracker.recordExerciseResult("a", repeticiones = 12, duracionSegundos = 90)
        tracker.advance()
        tracker.recordExerciseResult("b", repeticiones = 8, duracionSegundos = 60)

        assertEquals(2, tracker.pendingResults.size)
        assertEquals(2.5, tracker.totalDurationMinutes, 0.001)
        // 150 s → 3 minutos para mostrar (techo)
        assertEquals(3, tracker.totalMinutes)
        assertEquals("rutina-1", tracker.currentRoutineId)
        assertNotNull(tracker.startedAtIso)
    }

    @Test
    fun `attachEva adjunta al ultimo resultado registrado`() {
        tracker.start(routine("a", "b"))
        tracker.recordExerciseResult("a", 10, 60)
        tracker.attachEva(3)
        tracker.advance()
        tracker.recordExerciseResult("b", 10, 60)
        tracker.attachEva(7)

        assertEquals(3, tracker.pendingResults[0].escalaEVA)
        assertEquals(7, tracker.pendingResults[1].escalaEVA)
    }

    @Test
    fun `skip deja el eva del resultado en null`() {
        tracker.start(routine("a"))
        tracker.recordExerciseResult("a", 10, 60)

        assertNull(tracker.pendingResults.single().escalaEVA)
    }

    @Test
    fun `advance recorre la secuencia y devuelve null al terminar`() {
        tracker.start(routine("a", "b"))

        assertTrue(tracker.isCurrent("a"))
        assertEquals("b", tracker.advance()?.idEjercicio)
        assertNull(tracker.advance())
    }

    @Test
    fun `duracion minima respeta el piso que exige el backend`() {
        tracker.start(routine("a"))
        tracker.recordExerciseResult("a", 5, 0)

        assertEquals(0.01, tracker.totalDurationMinutes, 0.0)
        assertEquals(1, tracker.totalMinutes)
    }

    @Test
    fun `ejercicio suelto arranca la sesion retroactivamente sin rutina`() {
        tracker.recordExerciseResult("suelto", 10, 45)

        assertTrue(!tracker.isActive)
        assertNull(tracker.currentRoutineId)
        assertNotNull(tracker.startedAtIso)
        assertEquals(1, tracker.pendingResults.size)
    }

    @Test
    fun `clear resetea resultados rutina e inicio`() {
        tracker.start(routine("a"))
        tracker.recordExerciseResult("a", 10, 60)
        tracker.clear()

        assertTrue(tracker.pendingResults.isEmpty())
        assertNull(tracker.currentRoutineId)
        assertNull(tracker.startedAtIso)
        assertTrue(!tracker.isActive)
    }

    @Test
    fun `start desde ejercicio intermedio continua la secuencia desde ahi`() {
        tracker.start(routine("a", "b", "c"), fromIdEjercicio = "b")

        assertTrue(tracker.isCurrent("b"))
        assertEquals("c", tracker.advance()?.idEjercicio)
        assertNull(tracker.advance())
    }
}
