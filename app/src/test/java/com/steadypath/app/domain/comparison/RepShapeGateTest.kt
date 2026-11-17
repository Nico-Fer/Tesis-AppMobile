package com.steadypath.app.domain.comparison

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class RepShapeGateTest {

    private fun series(n: Int, f: (Double) -> Double): List<Float> =
        List(n) { i -> f(i.toDouble() / (n - 1)).toFloat() }

    /** Sube en [rise], sostiene y baja en [rise]: una repetición con pausa en el extremo. */
    private fun plateau(n: Int, rise: Double, wobble: Double = 0.0): List<Float> = series(n) { t ->
        val edge = minOf(t, 1 - t)
        if (edge < rise) 0.5 * (1 - cos(PI * edge / rise)) else 1 - wobble * 0.5 * (1 - cos(6 * PI * t))
    }

    @Test
    fun `un pulso coseno elevado puntua cerca de 1 a cualquier amplitud y largo`() {
        for (n in listOf(12, 40, 150)) {
            for (amp in listOf(0.05, 0.4, 1.5)) {
                val score = RepShapeGate.score(series(n) { amp * 0.5 * (1 - cos(2 * PI * it)) })
                assertTrue("n=$n amp=$amp → $score", score > 0.95)
            }
        }
    }

    @Test
    fun `un pulso triangular tambien pasa`() {
        val score = RepShapeGate.score(series(40) { if (it < 0.5) it * 2 else (1 - it) * 2 })
        assertTrue("$score", score > 0.9)
    }

    @Test
    fun `la pausa en el extremo pasa, aun temblando`() {
        val hold = RepShapeGate.score(plateau(60, rise = 0.15))
        assertTrue("$hold", hold > 0.9)
        // Solo contra el coseno, esta pausa con temblor no llegaba al umbral de 0.6.
        val wobbly = RepShapeGate.score(plateau(60, rise = 0.1, wobble = 0.3))
        assertTrue("$wobbly", wobbly >= 0.6)
    }

    @Test
    fun `una rampa monotona no es un pulso`() {
        val score = RepShapeGate.score(series(40) { it })
        assertTrue("$score", score < 0.5)
    }

    @Test
    fun `un pulso invertido vale 0`() {
        val score = RepShapeGate.score(series(40) { -0.5 * (1 - cos(2 * PI * it)) })
        assertEquals(0.0, score, 1e-6)
    }

    @Test
    fun `menos de 3 muestras no tiene forma`() {
        assertEquals(0.0, RepShapeGate.score(listOf(0f, 1f)), 0.0)
    }

    @Test
    fun `peakCount cuenta un pico por repeticion`() {
        assertEquals(1, RepShapeGate.peakCount(series(40) { 0.5 * (1 - cos(2 * PI * it)) }))
        // Dos reps pegadas: la meseta del puntaje no las separa, el conteo de picos sí.
        assertEquals(2, RepShapeGate.peakCount(series(60) { 0.5 * (1 - cos(4 * PI * it)) }))
        // Un temblor durante el sostén no llega a la mitad del extremo: sigue siendo uno.
        assertEquals(1, RepShapeGate.peakCount(plateau(60, rise = 0.1, wobble = 0.3)))
    }

    @Test
    fun `peakCount cuenta el pico de una rep chica que arranca cerca del umbral`() {
        // Desviación desde el reposo: arranca en 0.06 (cola del umbral), llega a 0.09 y baja.
        val dev = listOf(0.06f, 0.07f, 0.08f, 0.09f, 0.085f, 0.07f, 0.05f, 0.04f)
        assertEquals(1, RepShapeGate.peakCount(dev))
    }

    @Test
    fun `peakCount sin excursion devuelve cero`() {
        assertEquals(0, RepShapeGate.peakCount(emptyList()))
        assertEquals(0, RepShapeGate.peakCount(List(10) { 0f }))
    }

    @Test
    fun `resample conserva extremos e interpola`() {
        val out = RepShapeGate.resample(listOf(0f, 1f, 0f), 5)
        assertEquals(listOf(0f, 0.5f, 1f, 0.5f, 0f), out)
    }
}
