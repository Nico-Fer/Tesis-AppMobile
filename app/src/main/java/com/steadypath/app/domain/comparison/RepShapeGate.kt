package com.steadypath.app.domain.comparison

import com.steadypath.app.domain.comparison.dtw.ShapeSimilarity
import kotlin.math.PI
import kotlin.math.cos

/**
 * Gate de forma para una repetición candidata: ¿la excursión del conductor sale del reposo,
 * llega a un extremo y vuelve? Se mide con NCC centrada contra pulsos unitarios,
 * remuestreando la candidata a [SAMPLES] puntos, y con [peakCount].
 *
 * La NCC es invariante a amplitud por diseño: una sentadilla superficial y una profunda
 * tienen la misma forma. Lo que castiga es una rampa (deriva postural); dos reps pegadas
 * las detecta [peakCount]. La amplitud no se controla en ningún lado.
 */
object RepShapeGate {

    const val SAMPLES = 64

    /**
     * Ventanas de Tukey: α = 1 es el coseno elevado 0.5·(1 − cos 2πt), reposo → extremo →
     * reposo; α menor deja más tiempo en el extremo. Solo contra el coseno, una repetición
     * con pausa arriba, habitual en rehabilitación, puntuaba tan bajo como una rampa.
     */
    private val templates: List<List<Float>> = listOf(1.0, 0.5, 0.25).map { alpha -> tukey(alpha) }

    /**
     * [deviation] es la desviación del conductor respecto del reposo, ya con el signo de la
     * excursión aplicado (positiva en el extremo). Devuelve la mejor NCC en [0,1] contra las
     * plantillas; con menos de 3 muestras no hay forma que evaluar y devuelve 0.
     *
     * Al aceptar la meseta no distingue un sostén de varias jorobas sin bajar: eso lo
     * resuelve [peakCount], y quien use este puntaje debería exigir las dos cosas.
     */
    fun score(deviation: List<Float>): Double {
        if (deviation.size < 3) return 0.0
        val resampled = resample(deviation, SAMPLES)
        return templates.maxOf { ShapeSimilarity.ncc01(resampled, it) }
    }

    /**
     * Cantidad de picos separados por una caída de al menos [prominenceFraction] del
     * extremo: 1 para una repetición, 2 o más para reps pegadas sin volver al reposo. Un
     * temblor durante el sostén no llega a la mitad del extremo y no cuenta como pico.
     */
    fun peakCount(deviation: List<Float>, prominenceFraction: Float = PEAK_PROMINENCE_FRACTION): Int {
        if (deviation.isEmpty()) return 0
        val threshold = prominenceFraction * deviation.max()
        if (threshold <= 0f) return 0
        var count = 0
        var rising = true
        // La desviación se mide desde el reposo (0). En una rep chica el primer cuadro ya está
        // cerca del umbral de salida y desde ahí no se llega a la mitad del extremo.
        var low = minOf(0f, deviation.first())
        var high = Float.NEGATIVE_INFINITY
        for (value in deviation) {
            if (rising) {
                if (value - low >= threshold) {
                    count++
                    rising = false
                    high = value
                } else if (value < low) {
                    low = value
                }
            } else {
                if (value > high) high = value
                if (high - value >= threshold) {
                    rising = true
                    low = value
                }
            }
        }
        return count
    }

    private fun tukey(alpha: Double): List<Float> = List(SAMPLES) { i ->
        val t = i.toDouble() / (SAMPLES - 1)
        val edge = minOf(t, 1.0 - t)
        val value = if (edge < alpha / 2) 0.5 * (1.0 - cos(2.0 * PI * edge / alpha)) else 1.0
        value.toFloat()
    }

    /** Caída mínima, relativa al extremo, que separa dos picos en [peakCount]. */
    const val PEAK_PROMINENCE_FRACTION = 0.5f

    /** Remuestreo lineal a [n] puntos. */
    internal fun resample(values: List<Float>, n: Int): List<Float> {
        if (values.size == n) return values
        val last = values.size - 1
        return List(n) { i ->
            val pos = i.toDouble() * last / (n - 1)
            val lo = pos.toInt().coerceAtMost(last)
            val hi = (lo + 1).coerceAtMost(last)
            val frac = (pos - lo).toFloat()
            values[lo] + (values[hi] - values[lo]) * frac
        }
    }
}
