package com.steadypath.app.domain.comparison.dtw

import com.steadypath.app.domain.comparison.AngleFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ShapeSimilarityTest {

    private fun frames(vararg series: FloatArray): List<AngleFrame> {
        val d = series.size
        val length = series[0].size
        return List(length) { t ->
            AngleFrame(
                angles = FloatArray(d) { k -> series[k][t] },
                valid = BooleanArray(d) { true },
                timestampMs = t * 50L
            )
        }
    }

    private fun halfSine(amplitude: Float, length: Int = 30, base: Float = 0.4f): FloatArray =
        FloatArray(length) { i -> base + amplitude * sin(PI * i / (length - 1)).toFloat() }

    /** Camino identidad para secuencias del mismo largo. */
    private fun identityPath(length: Int): List<Pair<Int, Int>> = List(length) { it to it }

    @Test
    fun `secuencias identicas dan score 1`() {
        val u = frames(halfSine(1.4f))
        val r = frames(halfSine(1.4f))
        val score = ShapeSimilarity.score(u, r, identityPath(30), floatArrayOf(1f))
        assertEquals(1f, score, 1e-3f)
    }

    @Test
    fun `misma forma con la mitad de amplitud penaliza sobre la banda de tolerancia`() {
        val u = frames(halfSine(0.7f))
        val r = frames(halfSine(1.4f))
        val score = ShapeSimilarity.score(u, r, identityPath(30), floatArrayOf(1f))
        // NCC ~1 (misma forma), ratio 0.5 re-escalado por la tolerancia del 20% → 0.625.
        assertEquals(0.625f, score, 0.05f)
    }

    @Test
    fun `desvio de amplitud dentro de la tolerancia no penaliza`() {
        // 15% más de amplitud (overshoot leve): variabilidad normal, score neutro.
        val u = frames(halfSine(1.61f))
        val r = frames(halfSine(1.4f))
        val score = ShapeSimilarity.score(u, r, identityPath(30), floatArrayOf(1f))
        assertEquals(1f, score, 1e-2f)
    }

    @Test
    fun `forma opuesta da score 0`() {
        val u = frames(halfSine(-1.4f))
        val r = frames(halfSine(1.4f))
        val score = ShapeSimilarity.score(u, r, identityPath(30), floatArrayOf(1f))
        assertEquals(0f, score, 0.05f)
    }

    @Test
    fun `dimensiones planas en ambas secuencias son neutras`() {
        val flat = FloatArray(30) { 1.2f }
        val u = frames(halfSine(1.4f), flat)
        val r = frames(halfSine(1.4f), flat)
        val score = ShapeSimilarity.score(u, r, identityPath(30), floatArrayOf(1f, 0.5f))
        assertEquals(1f, score, 1e-3f)
    }

    @Test
    fun `usuario plano contra referencia con movimiento penaliza fuerte`() {
        val u = frames(FloatArray(30) { 0.4f })
        val r = frames(halfSine(1.4f))
        val score = ShapeSimilarity.score(u, r, identityPath(30), floatArrayOf(1f))
        assertTrue("score=$score debía ser ~0", score < 0.1f)
    }

    @Test
    fun `dimension invalida se excluye del promedio`() {
        val goodDim = halfSine(1.4f)
        val badDim = halfSine(0.1f)
        val u = List(30) { t ->
            AngleFrame(
                angles = floatArrayOf(goodDim[t], badDim[t]),
                // Segunda dimensión siempre inválida: no debe pesar.
                valid = booleanArrayOf(true, false),
                timestampMs = t * 50L
            )
        }
        val r = frames(halfSine(1.4f), halfSine(1.4f))
        val score = ShapeSimilarity.score(u, r, identityPath(30), floatArrayOf(1f, 1f))
        assertEquals(1f, score, 1e-3f)
    }

    @Test
    fun `camino vacio es neutro`() {
        val u = frames(halfSine(1.4f))
        assertEquals(1f, ShapeSimilarity.score(u, u, emptyList(), floatArrayOf(1f)), 0f)
    }

    @Test
    fun `el envolvimiento no infla la amplitud cerca de mas menos pi`() {
        // Ciclo que cruza ±π: de 3.0 a −3.0 rad hay 0.28 rad de desvío real, no ~6.
        val crossing = FloatArray(30) { i ->
            val raw = 3.0f + 0.28f * sin(PI * i / 29.0).toFloat()
            if (raw > PI.toFloat()) raw - 2 * PI.toFloat() else raw
        }
        val u = frames(crossing)
        val score = ShapeSimilarity.score(u, u, identityPath(30), floatArrayOf(1f))
        assertEquals(1f, score, 1e-3f)
    }

    /** Medio seno que cruza ±π (rodilla casi recta): los valores crudos saltan de +π a −π. */
    private fun wrappedHalfSine(amplitude: Float, base: Float, length: Int = 30): FloatArray {
        val pi = PI.toFloat()
        return FloatArray(length) { i ->
            val v = base + amplitude * sin(PI * i / (length - 1)).toFloat()
            if (v > pi) v - 2f * pi else v
        }
    }

    @Test
    fun `amplitud dentro de tolerancia cruzando mas menos pi no penaliza`() {
        // Sin des-envolver, la NCC cruda colapsa porque ambos ciclos cruzan π en índices distintos.
        val r = frames(wrappedHalfSine(amplitude = 0.5f, base = 2.9f))
        val u = frames(wrappedHalfSine(amplitude = 0.55f, base = 2.9f))
        val score = ShapeSimilarity.score(u, r, identityPath(30), floatArrayOf(1f))
        assertEquals(1f, score, 5e-2f)
    }

    @Test
    fun `media amplitud cruzando mas menos pi se castiga igual que sin cruce`() {
        val r = frames(wrappedHalfSine(amplitude = 0.6f, base = 2.9f))
        val u = frames(wrappedHalfSine(amplitude = 0.3f, base = 2.9f))
        val score = ShapeSimilarity.score(u, r, identityPath(30), floatArrayOf(1f))
        // NCC ~1 (misma forma), ratio 0.5 re-escalado por la tolerancia → 0.625.
        assertEquals(0.625f, score, 0.05f)
    }
}
