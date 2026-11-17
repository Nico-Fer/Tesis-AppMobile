package com.steadypath.app.domain.comparison.dtw

import com.steadypath.app.domain.comparison.AngleFrame
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Distancia local entre dos cuadros de ángulos, pluggable en el núcleo DTW.
 * Debe ser simétrica y ≥ 0.
 */
interface LocalDistance {
    fun distance(a: AngleFrame, b: AngleFrame): Double
}

/**
 * Euclidiana ponderada sobre la diferencia angular por dimensión, con envolvimiento:
 * `d_k = atan2(sin(a_k − b_k), cos(a_k − b_k))` — así d(350°, 10°) es 20° y no 340°.
 *
 * Las dimensiones inválidas en cualquiera de los dos cuadros se excluyen y el costo se
 * renormaliza por la suma de pesos válidos (RMS ponderado), para que cuadros con distinta
 * cantidad de dimensiones válidas produzcan costos comparables.
 */
class WrappedAngularDistance(private val weights: FloatArray) : LocalDistance {

    init {
        require(weights.isNotEmpty()) { "Se necesita al menos un peso" }
        require(weights.all { it > 0f }) { "Todos los pesos deben ser positivos" }
    }

    override fun distance(a: AngleFrame, b: AngleFrame): Double {
        val d = weights.size
        require(a.angles.size == d && b.angles.size == d) {
            "Dimensiones incompatibles: pesos=$d, a=${a.angles.size}, b=${b.angles.size}"
        }
        var sum = 0.0
        var weightSum = 0.0
        for (k in 0 until d) {
            if (!a.valid[k] || !b.valid[k]) continue
            val raw = (a.angles[k] - b.angles[k]).toDouble()
            val diff = atan2(sin(raw), cos(raw))
            val w = weights[k].toDouble()
            sum += w * diff * diff
            weightSum += w
        }
        // Punto de decisión: sin dimensiones válidas en común no hay evidencia de
        // diferencia → costo 0 (optimista, pero mantiene el camino factible).
        if (weightSum == 0.0) return 0.0
        return sqrt(sum / weightSum)
    }
}
