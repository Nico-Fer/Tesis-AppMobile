package com.steadypath.app.data.comparison

import android.content.Context
import android.util.Log
import com.steadypath.app.domain.comparison.RepMetricsSink
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Sink de calibración (solo builds DEBUG): appendea una fila CSV por repetición a
 * `Android/data/<pkg>/files/metrics/rep-metrics.csv` para análisis offline
 * (ver docs/calibration-protocol.md). Extraer con `adb pull`.
 */
class CsvRepMetricsSink @Inject constructor(
    @ApplicationContext private val context: Context
) : RepMetricsSink {

    private val file: File by lazy {
        File(context.getExternalFilesDir(null), "metrics/rep-metrics.csv").apply {
            parentFile?.mkdirs()
            if (!exists()) writeText(HEADER)
        }
    }

    override fun onRep(
        exerciseId: String,
        repIndex: Int,
        normalizedDistance: Double,
        dtwSimilarity: Float,
        shapeScore: Float,
        finalScore: Float
    ) {
        runCatching {
            file.appendText(
                "${System.currentTimeMillis()},$exerciseId,$repIndex," +
                    "$normalizedDistance,$dtwSimilarity,$shapeScore,$finalScore\n"
            )
        }.onFailure { Log.w(TAG, "No se pudo escribir la métrica de calibración", it) }
    }

    companion object {
        private const val TAG = "CsvRepMetrics"
        private const val HEADER =
            "timestampMs,exerciseId,repIndex,normalizedDistance,dtwSimilarity,shapeScore,finalScore\n"
    }
}
