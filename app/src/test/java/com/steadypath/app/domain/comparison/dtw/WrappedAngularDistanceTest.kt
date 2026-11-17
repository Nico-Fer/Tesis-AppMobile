package com.steadypath.app.domain.comparison.dtw

import com.steadypath.app.domain.comparison.AngleFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

class WrappedAngularDistanceTest {

    private fun deg(d: Float): Float = (d * PI / 180.0).toFloat()

    private fun frame(vararg angles: Float, valid: BooleanArray? = null) = AngleFrame(
        angles = angles.toList().toFloatArray(),
        valid = valid ?: BooleanArray(angles.size) { true },
        timestampMs = 0L
    )

    @Test
    fun `envolvimiento - distancia entre 350 y 10 grados es 20 no 340`() {
        val dist = WrappedAngularDistance(floatArrayOf(1f))
        val d = dist.distance(frame(deg(350f)), frame(deg(10f)))

        assertEquals(deg(20f).toDouble(), d, 1e-4)
        assertTrue("Debería ser chica, no ~340°", d < deg(30f))
    }

    @Test
    fun `envolvimiento simetrico alrededor de mas menos pi`() {
        val dist = WrappedAngularDistance(floatArrayOf(1f))
        val d1 = dist.distance(frame(3.1f), frame(-3.1f))

        // 3.1 y −3.1 rad están a 2π−6.2 ≈ 0.083 rad, no a 6.2.
        assertEquals(2.0 * PI - 6.2, d1, 1e-4)
    }

    @Test
    fun `distancia cero entre cuadros identicos`() {
        val dist = WrappedAngularDistance(floatArrayOf(1f, 1f))
        assertEquals(0.0, dist.distance(frame(0.5f, -1.2f), frame(0.5f, -1.2f)), 1e-9)
    }

    @Test
    fun `es simetrica`() {
        val dist = WrappedAngularDistance(floatArrayOf(1f, 2f))
        val a = frame(0.3f, 2.9f)
        val b = frame(-0.7f, -3.0f)
        assertEquals(dist.distance(a, b), dist.distance(b, a), 1e-9)
    }

    @Test
    fun `pesos ponderan cada dimension`() {
        val dist = WrappedAngularDistance(floatArrayOf(1f, 3f))
        val d = dist.distance(frame(0f, 0f), frame(0.2f, 0.4f))

        val expected = sqrt((1.0 * 0.2 * 0.2 + 3.0 * 0.4 * 0.4) / 4.0)
        assertEquals(expected, d, 1e-6)
    }

    @Test
    fun `dimension invalida se excluye y se renormaliza`() {
        val dist = WrappedAngularDistance(floatArrayOf(1f, 1f))
        val a = frame(0.5f, 99f, valid = booleanArrayOf(true, false))
        val b = frame(0.8f, -99f)

        // Solo cuenta la dimensión 0: RMS de una dimensión = |diff|.
        assertEquals(0.3, dist.distance(a, b), 1e-5)
    }

    @Test
    fun `sin dimensiones validas en comun devuelve cero`() {
        val dist = WrappedAngularDistance(floatArrayOf(1f, 1f))
        val a = frame(1f, 2f, valid = booleanArrayOf(true, false))
        val b = frame(3f, 4f, valid = booleanArrayOf(false, true))

        assertEquals(0.0, dist.distance(a, b), 0.0)
    }

    @Test
    fun `renormalizacion hace comparables cuadros con distinta validez`() {
        val dist = WrappedAngularDistance(floatArrayOf(1f, 1f))
        val full = dist.distance(frame(0f, 0f), frame(0.3f, 0.3f))
        val masked = dist.distance(
            frame(0f, 0f, valid = booleanArrayOf(true, false)),
            frame(0.3f, 0.3f)
        )

        // Misma diferencia por dimensión → mismo costo renormalizado.
        assertEquals(full, masked, 1e-6)
        assertTrue(abs(full - 0.3) < 1e-5)
    }

    // ── Precondiciones ───────────────────────────────────────────────────────

    @Test(expected = IllegalArgumentException::class)
    fun `sin pesos falla fuerte`() {
        WrappedAngularDistance(floatArrayOf())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `peso cero falla fuerte`() {
        WrappedAngularDistance(floatArrayOf(1f, 0f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `peso negativo falla fuerte`() {
        WrappedAngularDistance(floatArrayOf(-1f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cuadro con menos dimensiones que pesos falla fuerte`() {
        WrappedAngularDistance(floatArrayOf(1f, 1f)).distance(frame(0f), frame(0f, 0f))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `cuadro con mas dimensiones que pesos falla fuerte`() {
        WrappedAngularDistance(floatArrayOf(1f)).distance(frame(0f), frame(0f, 0f))
    }
}
