package com.steadypath.app.benchmark

import android.content.res.AssetManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Catálogo de clips del benchmark: `androidTest/assets/benchmark/clips.json`, generado por
 * `tools/benchmark/preparar_clips.py` junto con los `.mp4`. Los campos sobrantes del JSON
 * (procedencia, recorte, notas) se ignoran acá: documentan, no configuran.
 */
@Serializable
data class ClipManifest(
    val version: Int,
    /** `.tflite` con el que se calculó la línea de base de escritorio. */
    val modeloEscritorio: String,
    val clips: List<Clip>
) {
    @Serializable
    data class Clip(
        val id: String,
        val archivo: String,
        /** `app` (grabaciones propias, dominio de producción) o `uco` (sujetos held-out). */
        val grupo: String,
        /** Ejercicio con referencia DTW empaquetada; `null` = sólo rendimiento y precisión. */
        val exerciseId: String? = null,
        /** Semántica de `ImageInfo.rotationDegrees`: grados horarios hasta quedar derecho. */
        val rotationDegrees: Int,
        val fps: Double,
        val frames: Int,
        /** Reps que contó el generador de referencias (README de modelosCV), no un conteo manual. */
        val repsEsperadas: Int? = null,
        /** Tasa de detección del mismo `.tflite` en escritorio sobre este clip. */
        val deteccionEscritorio: Double
    )

    companion object {
        const val ASSET_DIR = "benchmark"

        private val json = Json { ignoreUnknownKeys = true }

        fun load(assets: AssetManager): ClipManifest {
            val text = runCatching {
                assets.open("$ASSET_DIR/clips.json").bufferedReader().use { it.readText() }
            }.getOrElse {
                throw IllegalStateException(
                    "Falta androidTest/assets/$ASSET_DIR/clips.json — generar los clips con " +
                        "tools/benchmark/preparar_clips.py (ver docs/benchmark-firebase-test-lab.md)",
                    it
                )
            }
            return json.decodeFromString(serializer(), text)
        }
    }
}
