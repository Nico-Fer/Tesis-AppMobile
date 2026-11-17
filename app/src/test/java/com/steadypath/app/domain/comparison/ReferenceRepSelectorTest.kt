package com.steadypath.app.domain.comparison

import com.steadypath.app.domain.comparison.dtw.Dtw
import com.steadypath.app.domain.comparison.dtw.LocalDistance
import com.steadypath.app.domain.comparison.dtw.WrappedAngularDistance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ReferenceRepSelectorTest {

    private val selector = ReferenceRepSelector(Dtw(WrappedAngularDistance(floatArrayOf(1f))))

    /** Rep sintética 1D: medio ciclo seno valle→pico→valle con amplitud dada. */
    private fun rep(amplitude: Float, frames: Int = 30, frameMs: Long = 50): List<AngleFrame> =
        List(frames) { i ->
            val angle = 0.5f + amplitude * sin(PI * i / (frames - 1)).toFloat()
            AngleFrame(floatArrayOf(angle), booleanArrayOf(true), i * frameMs)
        }

    @Test
    fun `con una sola rep la elige sin descartar`() {
        val selection = selector.select(listOf(rep(1f)))
        assertEquals(0, selection.selectedIndex)
        assertTrue(selection.discardedIndices.isEmpty())
    }

    @Test
    fun `elige el medoide entre reps similares`() {
        // Tres reps casi iguales y una con leve variación: cualquiera de las parecidas gana.
        val reps = listOf(rep(1.0f), rep(1.02f), rep(0.98f), rep(1.01f))
        val selection = selector.select(reps)
        assertTrue(selection.selectedIndex in reps.indices)
        assertTrue(selection.discardedIndices.isEmpty())
    }

    @Test
    fun `descarta la rep outlier y no la elige`() {
        // Tres reps consistentes y una con amplitud muy distinta (ensayo fallido).
        val reps = listOf(rep(1.0f), rep(1.05f), rep(0.95f), rep(0.2f))
        val selection = selector.select(reps)
        assertTrue("El outlier (índice 3) debía descartarse", 3 in selection.discardedIndices)
        assertTrue(selection.selectedIndex != 3)
    }

    @Test
    fun `con dos reps no descarta y elige una`() {
        val selection = selector.select(listOf(rep(1.0f), rep(0.5f)))
        assertTrue(selection.discardedIndices.isEmpty())
        assertTrue(selection.selectedIndex in 0..1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `lista vacia falla fuerte`() {
        selector.select(emptyList())
    }

    @Test
    fun `estimateFps usa la mediana de intervalos y resiste frames dropeados`() {
        // 20 fps nominal (50 ms) con un hueco de 200 ms por backpressure.
        val timestamps = longArrayOf(0, 50, 100, 300, 350, 400, 450)
        val rep = timestamps.map { AngleFrame(floatArrayOf(0f), booleanArrayOf(true), it) }
        assertEquals(20f, estimateFps(rep), 0.01f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `estimateFps con un solo frame falla fuerte`() {
        estimateFps(listOf(AngleFrame(floatArrayOf(0f), booleanArrayOf(true), 0L)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `estimateFps con timestamps no crecientes falla fuerte`() {
        val rep = longArrayOf(100, 100, 50).map { AngleFrame(floatArrayOf(0f), booleanArrayOf(true), it) }
        estimateFps(rep)
    }

    // ── cycleClosureErrorRad ─────────────────────────────────────────────────

    @Test
    fun `un ciclo cerrado tiene error de cierre cero`() {
        assertEquals(0f, cycleClosureErrorRad(rep(1f)), 1e-6f)
    }

    @Test
    fun `el error de cierre es la peor dimension con envolvimiento`() {
        // Dim 0 cierra casi (0.1 rad); dim 1 cruza ±π: 3.1 vs −3.1 distan 0.083, no 6.2.
        val rep = listOf(
            AngleFrame(floatArrayOf(0.0f, 3.1f), booleanArrayOf(true, true), 0L),
            AngleFrame(floatArrayOf(1.0f, 0.0f), booleanArrayOf(true, true), 50L),
            AngleFrame(floatArrayOf(0.1f, -3.1f), booleanArrayOf(true, true), 100L)
        )
        assertEquals(0.1f, cycleClosureErrorRad(rep), 1e-4f)
    }

    @Test
    fun `una toma que no vuelve al reposo supera la tolerancia de cierre`() {
        val open = rep(1f).dropLast(12) // se corta cerca del pico
        assertTrue(cycleClosureErrorRad(open) > FIRST_LAST_TOL_RAD)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cycleClosureErrorRad con repeticion vacia falla fuerte`() {
        cycleClosureErrorRad(emptyList())
    }

    // ── Caminos DTW infactibles ──────────────────────────────────────────────

    /** Distancia que vuelve infactible toda comparación contra la rep marcada con `poison`. */
    private class PoisonDistance(private val poison: Float) : LocalDistance {
        override fun distance(a: AngleFrame, b: AngleFrame): Double =
            if (a.angles[0] == poison || b.angles[0] == poison) Double.POSITIVE_INFINITY
            else kotlin.math.abs(a.angles[0] - b.angles[0]).toDouble()
    }

    private fun flat(value: Float, frames: Int = 5): List<AngleFrame> =
        List(frames) { AngleFrame(floatArrayOf(value), booleanArrayOf(true), it * 50L) }

    @Test
    fun `reps con camino DTW infactible se descartan y el selector igual elige una`() {
        // Una distancia infinita contamina la media de todas las reps (cada media incluye
        // la rep envenenada), así que ninguna sobrevive: el selector cae al conjunto
        // completo, elige una y marca como descartadas a las demás.
        val selector = ReferenceRepSelector(Dtw(PoisonDistance(poison = 9f)))
        val reps = listOf(flat(1.0f), flat(1.1f), flat(9f))

        val selection = selector.select(reps)

        assertTrue(selection.meanDistances.all { it.isInfinite() })
        assertTrue(selection.selectedIndex in reps.indices)
        assertEquals(reps.indices.filter { it != selection.selectedIndex }, selection.discardedIndices)
    }
}
