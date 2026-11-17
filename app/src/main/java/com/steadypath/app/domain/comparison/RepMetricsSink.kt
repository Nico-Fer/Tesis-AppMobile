package com.steadypath.app.domain.comparison

/**
 * Seam de telemetría por repetición para el protocolo de calibración: las métricas de
 * cada rep comparada se exportan (p. ej. a CSV en builds debug) para elegir umbrales
 * offline por separabilidad. Default no-op en producción.
 */
fun interface RepMetricsSink {
    fun onRep(
        exerciseId: String,
        repIndex: Int,
        normalizedDistance: Double,
        dtwSimilarity: Float,
        shapeScore: Float,
        finalScore: Float
    )

    companion object {
        val NO_OP = RepMetricsSink { _, _, _, _, _, _ -> }
    }
}
