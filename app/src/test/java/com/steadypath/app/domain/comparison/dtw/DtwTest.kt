package com.steadypath.app.domain.comparison.dtw

import com.steadypath.app.domain.comparison.AngleFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class DtwTest {

    private val dtw = Dtw(WrappedAngularDistance(floatArrayOf(1f)))

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun seq(vararg values: Float): List<AngleFrame> =
        values.map { AngleFrame(floatArrayOf(it), booleanArrayOf(true), 0L) }

    private fun seq(values: List<Float>): List<AngleFrame> =
        values.map { AngleFrame(floatArrayOf(it), booleanArrayOf(true), 0L) }

    /** Un ciclo de seno (0→2π) muestreado en [t] cuadros — misma curva, distinto ritmo. */
    private fun sampledSine(t: Int): List<AngleFrame> =
        seq(List(t) { i -> sin(2.0 * PI * i / (t - 1)).toFloat() })

    /** DTW completo sin banda, como oráculo de referencia para los tests. */
    private fun bruteForceDtw(s: List<AngleFrame>, t: List<AngleFrame>): Double {
        val local = WrappedAngularDistance(floatArrayOf(1f))
        val n = s.size
        val m = t.size
        val cost = Array(n) { DoubleArray(m) }
        for (i in 0 until n) {
            for (j in 0 until m) {
                val c = local.distance(s[i], t[j])
                cost[i][j] = c + when {
                    i == 0 && j == 0 -> 0.0
                    i == 0 -> cost[0][j - 1]
                    j == 0 -> cost[i - 1][0]
                    else -> min(cost[i - 1][j - 1], min(cost[i - 1][j], cost[i][j - 1]))
                }
            }
        }
        return cost[n - 1][m - 1]
    }

    // ── Casos base ───────────────────────────────────────────────────────────

    @Test
    fun `secuencias identicas dan distancia cero`() {
        val s = sampledSine(30)
        val result = dtw.distance(s, s)

        assertEquals(0.0, result.rawDistance, 1e-9)
        assertEquals(0.0, result.normalizedDistance, 1e-9)
    }

    @Test
    fun `copia estirada en el tiempo da distancia cero`() {
        // Cada cuadro duplicado: mismo movimiento a la mitad de velocidad.
        val reference = seq(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1.0f)
        val stretched = seq(0f, 0f, 0.2f, 0.2f, 0.4f, 0.4f, 0.6f, 0.6f, 0.8f, 0.8f, 1.0f, 1.0f)

        val result = dtw.distance(stretched, reference)

        assertEquals("DTW debe absorber la diferencia de ritmo", 0.0, result.rawDistance, 1e-9)
    }

    @Test
    fun `misma curva muestreada a distinto ritmo da distancia casi cero`() {
        val result = dtw.distance(sampledSine(30), sampledSine(50))

        assertTrue(
            "Esperaba ≈0, fue ${result.normalizedDistance}",
            result.normalizedDistance < 0.05
        )
    }

    @Test
    fun `movimiento claramente distinto da distancia grande`() {
        val still = seq(List(20) { 0f })
        val oscillating = seq(List(20) { i -> if (i % 2 == 0) 2f else -2f })

        val result = dtw.distance(still, oscillating)

        assertTrue(
            "Esperaba distancia grande, fue ${result.normalizedDistance}",
            result.normalizedDistance > 0.5
        )
    }

    // ── Banda de Sakoe-Chiba ─────────────────────────────────────────────────

    @Test
    fun `banda ancha coincide con DTW completo`() {
        val rng = Random(7)
        val s = seq(List(15) { (rng.nextFloat() - 0.5f) * 2f })
        val t = seq(List(12) { (rng.nextFloat() - 0.5f) * 2f })

        val banded = dtw.distance(s, t, windowRatio = 1f)

        assertEquals(bruteForceDtw(s, t), banded.rawDistance, 1e-9)
    }

    @Test
    fun `banda angosta factible nunca mejora al DTW completo`() {
        val rng = Random(11)
        val s = seq(List(20) { (rng.nextFloat() - 0.5f) * 2f })
        val t = seq(List(20) { (rng.nextFloat() - 0.5f) * 2f })

        val narrow = dtw.distance(s, t, windowRatio = 0.05f)
        val full = bruteForceDtw(s, t)

        assertTrue(narrow.isFeasible)
        assertTrue(
            "Banda angosta (${narrow.rawDistance}) < completo ($full)",
            narrow.rawDistance >= full - 1e-9
        )
    }

    @Test
    fun `banda infactible devuelve infinito`() {
        // n=2 vs m=10 con w=1: la última celda queda fuera de la banda.
        val result = dtw.distance(seq(0f, 1f), sampledSine(10), windowRatio = 0.1f)

        assertFalse(result.isFeasible)
        assertTrue(result.rawDistance.isInfinite())
        assertTrue(result.normalizedDistance.isInfinite())
    }

    // ── Normalización ────────────────────────────────────────────────────────

    @Test
    fun `normalizacion hace comparables repeticiones de distinta duracion`() {
        val reference = sampledSine(50)
        val fast = dtw.distance(sampledSine(30), reference)
        val slow = dtw.distance(sampledSine(70), reference)

        assertTrue(fast.normalizedDistance < 0.1)
        assertTrue(slow.normalizedDistance < 0.1)
        assertTrue(
            "fast=${fast.normalizedDistance} vs slow=${slow.normalizedDistance}",
            abs(fast.normalizedDistance - slow.normalizedDistance) < 0.05
        )
    }

    // ── Camino ───────────────────────────────────────────────────────────────

    @Test
    fun `camino valido - frontera monotonia continuidad`() {
        val s = sampledSine(20)
        val t = sampledSine(35)
        val result = dtw.distance(s, t, computePath = true)

        val path = result.path!!
        assertEquals(0 to 0, path.first())
        assertEquals(19 to 34, path.last())
        for (k in 1 until path.size) {
            val di = path[k].first - path[k - 1].first
            val dj = path[k].second - path[k - 1].second
            assertTrue("Paso inválido ($di,$dj)", (di == 0 || di == 1) && (dj == 0 || dj == 1))
            assertTrue("Paso estacionario", di + dj > 0)
        }
    }

    @Test
    fun `modo camino y modo solo-distancia coinciden`() {
        val rng = Random(23)
        val s = seq(List(25) { (rng.nextFloat() - 0.5f) * 2f })
        val t = seq(List(18) { (rng.nextFloat() - 0.5f) * 2f })

        val withPath = dtw.distance(s, t, computePath = true)
        val distanceOnly = dtw.distance(s, t)

        assertEquals(withPath.rawDistance, distanceOnly.rawDistance, 1e-9)
        assertEquals(withPath.normalizedDistance, distanceOnly.normalizedDistance, 1e-9)
        // La normalización usa exactamente la longitud del camino.
        assertEquals(
            withPath.rawDistance / withPath.path!!.size,
            withPath.normalizedDistance,
            1e-12
        )
    }

    @Test
    fun `sin computePath no se materializa el camino`() {
        val result = dtw.distance(sampledSine(10), sampledSine(10))
        assertEquals(null, result.path)
    }

    // ── Bordes ───────────────────────────────────────────────────────────────

    @Test
    fun `secuencias de un solo cuadro`() {
        val result = dtw.distance(seq(0.5f), seq(0.7f))

        assertEquals(0.2, result.rawDistance, 1e-5)
        assertEquals(0.2, result.normalizedDistance, 1e-5)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `secuencia vacia falla fuerte`() {
        dtw.distance(emptyList(), seq(1f))
    }
}
