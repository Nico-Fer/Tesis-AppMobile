package com.steadypath.app.domain.comparison.dtw

import com.steadypath.app.domain.comparison.AngleFrame
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Resultado de un alineamiento DTW.
 *
 * @property rawDistance costo acumulado a lo largo del camino óptimo. [Double.POSITIVE_INFINITY]
 *   si la banda de Sakoe-Chiba volvió infactible el camino.
 * @property normalizedDistance [rawDistance] dividido por la cantidad de pasos del camino,
 *   comparable entre repeticiones de distinta duración.
 * @property path pares (i, j) del alineamiento, solo si se pidió con `computePath`.
 */
data class DtwResult(
    val rawDistance: Double,
    val normalizedDistance: Double,
    val path: List<Pair<Int, Int>>? = null
) {
    val isFeasible: Boolean get() = rawDistance.isFinite()
}

/**
 * Dynamic Time Warping multidimensional sobre secuencias de [AngleFrame]. Kotlin puro,
 * sin dependencias de Android — testeable en JVM.
 *
 * Recurrencia clásica con frontera, monotonía y continuidad:
 * `D(i,j) = local(i,j) + min(D(i−1,j), D(i,j−1), D(i−1,j−1))`.
 *
 * **Banda de Sakoe-Chiba** relativa a la diagonal del grid (n≠m): la celda (i,j) es válida
 * si `|j − i·m/n| ≤ w`, con `w = windowRatio·max(n,m)`. Además de recortar el cómputo a
 * O(n·w), la banda impide alineamientos absurdos (media repetición mapeada a un solo
 * cuadro). Una banda demasiado angosta puede volver el camino infactible: en ese caso se
 * devuelve `+∞` y el llamador decide (reintentar con banda más ancha o descartar).
 *
 * Memoria: modo solo-distancia usa dos filas rodantes (costo + contador de pasos para la
 * normalización exacta); la matriz completa se materializa solo con `computePath = true`.
 * El lazo interno no aloca (arrays primitivos, sin boxing).
 */
class Dtw(private val localDistance: LocalDistance) {

    fun distance(
        s: List<AngleFrame>,
        t: List<AngleFrame>,
        windowRatio: Float = DEFAULT_WINDOW_RATIO,
        computePath: Boolean = false
    ): DtwResult {
        require(s.isNotEmpty() && t.isNotEmpty()) { "Las secuencias no pueden ser vacías" }
        require(windowRatio > 0f) { "windowRatio debe ser > 0 (era $windowRatio)" }

        val n = s.size
        val m = t.size
        val w = max(1.0, windowRatio.toDouble() * max(n, m))
        val slope = m.toDouble() / n.toDouble()

        return if (computePath) fullMatrix(s, t, w, slope) else rollingRows(s, t, w, slope)
    }

    private fun bandLo(i: Int, slope: Double, w: Double): Int =
        max(0, floor(i * slope - w).toInt())

    private fun bandHi(i: Int, slope: Double, w: Double, m: Int): Int =
        min(m - 1, ceil(i * slope + w).toInt())

    private fun rollingRows(
        s: List<AngleFrame>,
        t: List<AngleFrame>,
        w: Double,
        slope: Double
    ): DtwResult {
        val n = s.size
        val m = t.size
        var prevCost = DoubleArray(m) { Double.POSITIVE_INFINITY }
        var currCost = DoubleArray(m)
        var prevSteps = IntArray(m)
        var currSteps = IntArray(m)

        for (i in 0 until n) {
            currCost.fill(Double.POSITIVE_INFINITY)
            val jLo = bandLo(i, slope, w)
            val jHi = bandHi(i, slope, w, m)
            val si = s[i]
            for (j in jLo..jHi) {
                if (i == 0 && j == 0) {
                    currCost[0] = localDistance.distance(si, t[0])
                    currSteps[0] = 1
                    continue
                }
                var best = Double.POSITIVE_INFINITY
                var bestSteps = 0
                if (i > 0) {
                    val c = prevCost[j]
                    if (c < best) { best = c; bestSteps = prevSteps[j] }
                }
                if (j > 0) {
                    val c = currCost[j - 1]
                    if (c < best) { best = c; bestSteps = currSteps[j - 1] }
                }
                if (i > 0 && j > 0) {
                    val c = prevCost[j - 1]
                    // Empate → diagonal: camino más corto y alineamiento más natural.
                    if (c <= best) { best = c; bestSteps = prevSteps[j - 1] }
                }
                if (best == Double.POSITIVE_INFINITY) continue
                currCost[j] = localDistance.distance(si, t[j]) + best
                currSteps[j] = bestSteps + 1
            }
            val swapCost = prevCost; prevCost = currCost; currCost = swapCost
            val swapSteps = prevSteps; prevSteps = currSteps; currSteps = swapSteps
        }

        val raw = prevCost[m - 1]
        if (!raw.isFinite()) return INFEASIBLE
        return DtwResult(rawDistance = raw, normalizedDistance = raw / prevSteps[m - 1])
    }

    private fun fullMatrix(
        s: List<AngleFrame>,
        t: List<AngleFrame>,
        w: Double,
        slope: Double
    ): DtwResult {
        val n = s.size
        val m = t.size
        val cost = Array(n) { DoubleArray(m) { Double.POSITIVE_INFINITY } }
        val steps = Array(n) { IntArray(m) }
        // 0 = sin predecesor, 1 = diagonal, 2 = vertical (i−1), 3 = horizontal (j−1)
        val back = Array(n) { ByteArray(m) }

        for (i in 0 until n) {
            val jLo = bandLo(i, slope, w)
            val jHi = bandHi(i, slope, w, m)
            val si = s[i]
            for (j in jLo..jHi) {
                if (i == 0 && j == 0) {
                    cost[0][0] = localDistance.distance(si, t[0])
                    steps[0][0] = 1
                    continue
                }
                var best = Double.POSITIVE_INFINITY
                var bestSteps = 0
                var from: Byte = 0
                if (i > 0) {
                    val c = cost[i - 1][j]
                    if (c < best) { best = c; bestSteps = steps[i - 1][j]; from = 2 }
                }
                if (j > 0) {
                    val c = cost[i][j - 1]
                    if (c < best) { best = c; bestSteps = steps[i][j - 1]; from = 3 }
                }
                if (i > 0 && j > 0) {
                    val c = cost[i - 1][j - 1]
                    if (c <= best) { best = c; bestSteps = steps[i - 1][j - 1]; from = 1 }
                }
                if (best == Double.POSITIVE_INFINITY) continue
                cost[i][j] = localDistance.distance(si, t[j]) + best
                steps[i][j] = bestSteps + 1
                back[i][j] = from
            }
        }

        val raw = cost[n - 1][m - 1]
        if (!raw.isFinite()) return INFEASIBLE

        val path = ArrayList<Pair<Int, Int>>(steps[n - 1][m - 1])
        var i = n - 1
        var j = m - 1
        while (true) {
            path.add(i to j)
            when (back[i][j]) {
                1.toByte() -> { i--; j-- }
                2.toByte() -> i--
                3.toByte() -> j--
                else -> break
            }
        }
        path.reverse()

        return DtwResult(
            rawDistance = raw,
            normalizedDistance = raw / path.size,
            path = path
        )
    }

    companion object {
        /** Semiancho de banda como fracción de max(n,m). TODO: calibrar con datos. */
        const val DEFAULT_WINDOW_RATIO = 0.2f

        private val INFEASIBLE = DtwResult(
            rawDistance = Double.POSITIVE_INFINITY,
            normalizedDistance = Double.POSITIVE_INFINITY
        )
    }
}
