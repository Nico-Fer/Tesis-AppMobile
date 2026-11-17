package com.steadypath.app.domain.comparison

import com.steadypath.app.domain.comparison.dtw.Dtw

/**
 * Elige la repetición canónica entre las N capturadas por el fisioterapeuta: el MEDOIDE
 * por distancia DTW (la rep con menor distancia media al resto), descartando outliers.
 *
 * Kotlin puro, sin dependencias de Android — testeable en JVM. Promediar reps (DBA)
 * queda fuera de alcance: una rep canónica bien elegida basta.
 */
class ReferenceRepSelector(
    private val dtw: Dtw,
    private val outlierFactor: Double = DEFAULT_OUTLIER_FACTOR
) {

    /**
     * @property selectedIndex índice de la rep elegida dentro de la lista original.
     * @property discardedIndices reps descartadas por outlier (o camino DTW infactible).
     * @property meanDistances distancia media de cada rep al resto (diagnóstico/UI).
     */
    data class Selection(
        val selectedIndex: Int,
        val discardedIndices: List<Int>,
        val meanDistances: List<Double>
    )

    fun select(reps: List<List<AngleFrame>>): Selection {
        require(reps.isNotEmpty()) { "No hay repeticiones para seleccionar" }
        if (reps.size == 1) return Selection(0, emptyList(), listOf(0.0))

        val n = reps.size
        // Matriz simétrica de distancias normalizadas (DTW normalizado es ~simétrico;
        // computar una sola dirección alcanza para ordenar medoides).
        val dist = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                val d = dtw.distance(reps[i], reps[j]).normalizedDistance
                dist[i][j] = d
                dist[j][i] = d
            }
        }

        val meanDistances = List(n) { i ->
            (0 until n).filter { it != i }.sumOf { dist[i][it] } / (n - 1)
        }

        // Con ≤2 reps no hay base estadística para descartar: se elige la de menor media.
        val discarded = if (n <= 2) {
            emptyList()
        } else {
            val finiteMeans = meanDistances.filter { it.isFinite() }.sorted()
            val median = finiteMeans.getOrElse(finiteMeans.size / 2) { Double.POSITIVE_INFINITY }
            meanDistances.indices.filter { i ->
                !meanDistances[i].isFinite() || meanDistances[i] > outlierFactor * median
            }
        }

        val survivors = meanDistances.indices.filterNot { it in discarded }
            .ifEmpty { meanDistances.indices.toList() }

        // Medoide entre sobrevivientes: menor distancia media a los demás sobrevivientes.
        val selected = survivors.minByOrNull { i ->
            survivors.filter { it != i }.sumOf { dist[i][it] }
        } ?: 0

        return Selection(
            selectedIndex = selected,
            discardedIndices = discarded.filter { it != selected },
            meanDistances = meanDistances
        )
    }

    companion object {
        /** Una rep es outlier si su distancia media supera este múltiplo de la mediana. TODO: calibrar con datos. */
        const val DEFAULT_OUTLIER_FACTOR = 2.0
    }
}

/**
 * Estima los fps de una repetición por la mediana de los intervalos entre frames —
 * robusta a frames dropeados por backpressure (KEEP_ONLY_LATEST).
 */
fun estimateFps(rep: List<AngleFrame>): Float {
    require(rep.size >= 2) { "Se necesitan al menos 2 frames para estimar fps" }
    val intervals = rep.zipWithNext { a, b -> (b.timestampMs - a.timestampMs).toDouble() }
        .filter { it > 0 }
        .sorted()
    require(intervals.isNotEmpty()) { "Timestamps no crecientes: no se puede estimar fps" }
    val medianMs = intervals[intervals.size / 2]
    return (1000.0 / medianMs).toFloat()
}

/** Tolerancia de cierre de ciclo (paridad con schema.py de modelosCV). TODO: calibrar. */
const val FIRST_LAST_TOL_RAD = 0.35f

/**
 * Máximo |wrap_diff(primer frame, último frame)| entre dimensiones: una referencia válida
 * cierra el ciclo reposo→extremo→reposo, así que primero ≈ último en cada ángulo.
 */
fun cycleClosureErrorRad(rep: List<AngleFrame>): Float {
    require(rep.isNotEmpty()) { "Repetición vacía" }
    val first = rep.first().angles
    val last = rep.last().angles
    var worst = 0f
    for (k in first.indices) {
        val raw = (first[k] - last[k]).toDouble()
        val diff = kotlin.math.abs(kotlin.math.atan2(kotlin.math.sin(raw), kotlin.math.cos(raw)))
        worst = maxOf(worst, diff.toFloat())
    }
    return worst
}
