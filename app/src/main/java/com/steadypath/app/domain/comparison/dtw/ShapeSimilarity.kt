package com.steadypath.app.domain.comparison.dtw

import com.steadypath.app.domain.comparison.AngleFrame
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Similitud de FORMA entre la repetición del usuario y la referencia, sobre las
 * secuencias ya alineadas por el camino DTW. Complementa la distancia DTW, que absorbe
 * parcialmente los déficits de amplitud vía warping.
 *
 * Por dimensión se componen dos factores:
 * 1. **NCC centrada** (correlación cruzada normalizada): mide si la *forma* del ciclo
 *    coincide. Es deliberadamente invariante a amplitud, por eso sola no alcanza.
 * 2. **Ratio de amplitud**: `min(amp_u, amp_r) / max(amp_u, amp_r)`, con la amplitud
 *    medida como el máximo desvío (con envolvimiento) respecto del inicio del ciclo.
 *    Este factor es el que castiga "llegó a 0.7 rad donde la referencia llega a 1.4".
 *
 * El resultado es la media ponderada por peso de `ncc⁺ × ratio` sobre las dimensiones
 * con evidencia suficiente; sin evidencia devuelve 1 (neutro para la composición).
 * Kotlin puro, testeable en JVM.
 */
object ShapeSimilarity {

    private const val MIN_ALIGNED_PAIRS = 3
    private const val FLAT_VARIANCE_EPS = 1e-6
    private const val FLAT_AMPLITUDE_EPS = 1e-3f

    /**
     * Desvío de amplitud tolerado sin castigo (±20%): la variabilidad humana normal
     * —levantar apenas más o menos que la referencia— no es un error de ejecución.
     * TODO: calibrar con datos.
     */
    private const val AMPLITUDE_TOLERANCE = 0.2

    fun score(
        user: List<AngleFrame>,
        reference: List<AngleFrame>,
        path: List<Pair<Int, Int>>,
        weights: FloatArray
    ): Float {
        if (path.isEmpty()) return 1f
        val d = weights.size
        var weighted = 0.0
        var weightSum = 0.0

        for (k in 0 until d) {
            val u = ArrayList<Float>(path.size)
            val r = ArrayList<Float>(path.size)
            for ((i, j) in path) {
                if (!user[i].valid[k] || !reference[j].valid[k]) continue
                u.add(user[i].angles[k])
                r.add(reference[j].angles[k])
            }
            if (u.size < MIN_ALIGNED_PAIRS) continue

            // Des-envueltas: un ciclo que cruza ±π (rodilla casi recta) rompe la NCC cruda.
            val uw = unwrap(u)
            val rw = unwrap(r)
            val shape = ncc01(uw, rw) * amplitudeRatio(uw, rw)
            weighted += weights[k] * shape
            weightSum += weights[k]
        }

        if (weightSum == 0.0) return 1f
        return (weighted / weightSum).toFloat()
    }

    /** NCC centrada, recortada a [0,1]: correlación negativa = forma opuesta = 0. */
    internal fun ncc01(u: List<Float>, r: List<Float>): Double {
        val n = u.size
        val meanU = u.sumOf { it.toDouble() } / n
        val meanR = r.sumOf { it.toDouble() } / n
        var cov = 0.0
        var varU = 0.0
        var varR = 0.0
        for (t in 0 until n) {
            val du = u[t] - meanU
            val dr = r[t] - meanR
            cov += du * dr
            varU += du * du
            varR += dr * dr
        }
        // Secuencias planas: sin forma que correlacionar — neutro, decide el ratio.
        if (varU < FLAT_VARIANCE_EPS || varR < FLAT_VARIANCE_EPS) return 1.0
        return (cov / sqrt(varU * varR)).coerceIn(0.0, 1.0)
    }

    /** Des-envuelve delta a delta: la secuencia queda continua aunque cruce ±π. */
    private fun unwrap(values: List<Float>): List<Float> {
        val out = ArrayList<Float>(values.size)
        var acc = values.first()
        out.add(acc)
        for (t in 1 until values.size) {
            acc += wrapDiff(values[t], values[t - 1])
            out.add(acc)
        }
        return out
    }

    /** Máximo desvío respecto del inicio del ciclo, sobre la secuencia des-envuelta. */
    private fun amplitude(values: List<Float>): Float {
        val start = values.first()
        var maxDev = 0f
        for (v in values) {
            val dev = abs(v - start)
            if (dev > maxDev) maxDev = dev
        }
        return maxDev
    }

    private fun amplitudeRatio(u: List<Float>, r: List<Float>): Double {
        val ampU = amplitude(u)
        val ampR = amplitude(r)
        val hi = max(ampU, ampR)
        // Ambas planas (p. ej. ángulo de control postural quieto): amplitudes iguales.
        if (hi < FLAT_AMPLITUDE_EPS) return 1.0
        val ratio = (min(ampU, ampR) / hi).toDouble()
        // Banda muerta: dentro de la tolerancia el ratio es 1; afuera, rampa proporcional.
        return (ratio / (1.0 - AMPLITUDE_TOLERANCE)).coerceAtMost(1.0)
    }

    private fun wrapDiff(a: Float, b: Float): Float {
        val raw = (a - b).toDouble()
        return atan2(sin(raw), cos(raw)).toFloat()
    }
}
