package com.steadypath.app.domain.comparison

import com.steadypath.app.domain.comparison.dtw.Dtw
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
}
