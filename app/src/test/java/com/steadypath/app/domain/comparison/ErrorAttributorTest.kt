package com.steadypath.app.domain.comparison

import com.steadypath.app.domain.comparison.dtw.Dtw
import com.steadypath.app.domain.comparison.dtw.WrappedAngularDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ErrorAttributorTest {

    private val attributor = ErrorAttributor()
    private val dtw = Dtw(WrappedAngularDistance(floatArrayOf(1f, 1f)))

    /** Definición 2D: rodilla (driver) + cadera, como flexion_rodilla_izq del catálogo. */
    private val definition = MovementPatternCatalog.bySlug("flexion_rodilla_izq")!!
        .toExerciseDefinition("test")

    /**
     * Rep sintética 2D: la rodilla hace medio ciclo seno 0.4→0.4+amplitud→0.4; la cadera
     * queda plana en `hipAngle` (o sigue su propia amplitud si se indica).
     */
    private fun rep(
        kneeAmplitude: Float,
        frames: Int = 40,
        frameMs: Long = 50,
        hipAmplitude: Float = 0f
    ): List<AngleFrame> = List(frames) { i ->
        val phase = sin(PI * i / (frames - 1)).toFloat()
        AngleFrame(
            angles = floatArrayOf(0.4f + kneeAmplitude * phase, 1.2f + hipAmplitude * phase),
            valid = booleanArrayOf(true, true),
            timestampMs = i * frameMs
        )
    }

    private fun attribute(user: List<AngleFrame>, reference: List<AngleFrame>): List<CorrectionHint> {
        val result = dtw.distance(user, reference, computePath = true)
        return attributor.attribute(user, reference, result.path!!, definition)
    }

    @Test
    fun `ejecucion identica no genera hints`() {
        val reference = rep(kneeAmplitude = 1.4f)
        assertTrue(attribute(rep(kneeAmplitude = 1.4f), reference).isEmpty())
    }

    @Test
    fun `amplitud corta genera UNDERSHOOT del angulo conductor`() {
        val reference = rep(kneeAmplitude = 1.4f)
        val hints = attribute(rep(kneeAmplitude = 0.5f), reference)
        assertTrue("Esperaba algún hint", hints.isNotEmpty())
        val top = hints.first()
        assertEquals("rodilla_izq", top.angleName)
        assertEquals(ErrorDirection.UNDERSHOOT, top.direction)
        assertTrue("El hint del conductor debe marcarse como driver", top.isDriver)
    }

    @Test
    fun `amplitud corta con movimiento hacia negativo tambien es UNDERSHOOT`() {
        // Mismo ciclo pero el ángulo baja en vez de subir: el signo no debe confundir.
        val reference = rep(kneeAmplitude = -1.4f)
        val hints = attribute(rep(kneeAmplitude = -0.5f), reference)
        assertTrue(hints.isNotEmpty())
        assertEquals(ErrorDirection.UNDERSHOOT, hints.first().direction)
    }

    @Test
    fun `pasarse de amplitud genera OVERSHOOT`() {
        val reference = rep(kneeAmplitude = 0.8f)
        val hints = attribute(rep(kneeAmplitude = 1.6f), reference)
        assertTrue(hints.isNotEmpty())
        assertEquals(ErrorDirection.OVERSHOOT, hints.first().direction)
        assertEquals("rodilla_izq", hints.first().angleName)
    }

    @Test
    fun `error localizado en la cadera se atribuye a la cadera`() {
        val reference = rep(kneeAmplitude = 1.4f, hipAmplitude = 0f)
        // Rodilla perfecta pero el tronco compensa: la cadera se mueve 0.9 rad de más.
        val user = rep(kneeAmplitude = 1.4f, hipAmplitude = 0.9f)
        val hints = attribute(user, reference)
        assertTrue(hints.isNotEmpty())
        assertEquals("cadera_izq", hints.first().angleName)
        assertTrue("La cadera secundaria no es driver", !hints.first().isDriver)
    }

    @Test
    fun `ejecucion uniformemente lenta genera TOO_SLOW`() {
        val reference = rep(kneeAmplitude = 1.4f, frames = 30)
        // Misma trayectoria con el triple de frames: mismo camino espacial, tempo 3x.
        val user = rep(kneeAmplitude = 1.4f, frames = 90)
        val hints = attribute(user, reference)
        assertTrue(hints.isNotEmpty())
        assertTrue(hints.all { it.direction == ErrorDirection.TOO_SLOW })
    }

    @Test
    fun `ejecucion moderadamente lenta se tolera`() {
        // 2.5x más lento: dentro de la tolerancia asimétrica (3x) — el público objetivo
        // ejecuta más lento que la referencia y sostener el extremo no es un error.
        val reference = rep(kneeAmplitude = 1.4f, frames = 30)
        val user = rep(kneeAmplitude = 1.4f, frames = 74)
        assertTrue(attribute(user, reference).isEmpty())
    }

    @Test
    fun `ejecucion uniformemente rapida genera TOO_FAST`() {
        val reference = rep(kneeAmplitude = 1.4f, frames = 90)
        val user = rep(kneeAmplitude = 1.4f, frames = 30)
        val hints = attribute(user, reference)
        assertTrue(hints.isNotEmpty())
        assertTrue(hints.all { it.direction == ErrorDirection.TOO_FAST })
    }

    @Test
    fun `misma velocidad real con distinta cadencia de frames no genera hints`() {
        // Regresión: la referencia va a ~30 fps y la inferencia en vivo a ~10 fps — comparar
        // cantidad de cuadros marcaba TOO_FAST espurio a velocidad real idéntica.
        val reference = rep(kneeAmplitude = 1.4f, frames = 90, frameMs = 33)
        val user = rep(kneeAmplitude = 1.4f, frames = 30, frameMs = 100)
        assertTrue(attribute(user, reference).isEmpty())
    }

    @Test
    fun `devuelve como maximo maxHints ordenados por severidad`() {
        val reference = rep(kneeAmplitude = 1.4f, frames = 30)
        // Corto en amplitud Y lento: múltiples hints, gana el más severo y hay a lo sumo 2.
        val user = rep(kneeAmplitude = 0.4f, frames = 90)
        val hints = attribute(user, reference)
        assertTrue(hints.size <= ErrorAttributor.DEFAULT_MAX_HINTS)
        assertTrue(hints.zipWithNext().all { (a, b) -> a.severity >= b.severity })
    }

    @Test
    fun `camino vacio devuelve lista vacia`() {
        val reference = rep(kneeAmplitude = 1.4f)
        assertTrue(
            attributor.attribute(rep(1.4f), reference, emptyList(), definition).isEmpty()
        )
    }
}
