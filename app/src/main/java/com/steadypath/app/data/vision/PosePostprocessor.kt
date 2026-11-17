package com.steadypath.app.data.vision

import com.steadypath.app.domain.model.Keypoint
import com.steadypath.app.domain.model.KeypointType
import com.steadypath.app.domain.model.PoseFrame
import kotlin.math.max
import kotlin.math.min

/**
 * Decodifica la salida cruda de YOLOv8-Pose en un [PoseFrame] de dominio.
 *
 * Núcleo **puro** (sin dependencias de Android ni TFLite) para poder testearlo en la JVM.
 * Recibe la salida aplanada del modelo; la cantidad de canales, el orden de keypoints y la
 * disposición (`[canales, detecciones]` o su transpuesta) salen de [PoseModelSpec], que
 * resuelve el indexado en [PoseModelSpec.indexOf]. Los canales son
 * `[cx, cy, w, h, score, (x, y, conf) × keypoints]`, con coordenadas en el espacio de
 * entrada del modelo (cuadrado, con letterbox).
 *
 * Pasos: lleva las coordenadas a píxeles según [PoseModelSpec.coordinateSpace] → filtra por
 * confianza → NMS (IoU) → selecciona la persona de mayor score → des-letterboxea y normaliza
 * los keypoints al espacio de la imagen original (sin clampear: ver [toPoseFrame]).
 */
class PosePostprocessor(
    private val spec: PoseModelSpec = PoseModelSpec.ACTIVE,
    private val confidenceThreshold: Float = spec.confidenceThreshold,
    private val iouThreshold: Float = spec.iouThreshold
) {

    /** Keypoints que se mapean al dominio: el modelo puede emitir más de los que modelamos. */
    private val mappedKeypoints = min(spec.keypointCount, KeypointType.COUNT)

    /** Detección en espacio de modelo: box xyxy + score + keypoints crudos (x,y,conf)×N. */
    internal data class Detection(
        val score: Float,
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val keypoints: FloatArray
    )

    fun process(
        output: FloatArray,
        params: LetterboxParams,
        sourceWidth: Int,
        sourceHeight: Int,
        timestampMs: Long
    ): PoseFrame? {
        val candidates = decode(output)
        if (candidates.isEmpty()) return null

        val survivors = nms(candidates)
        // Persona principal: mayor score (la lista de NMS ya viene ordenada desc).
        val best = survivors.firstOrNull() ?: return null

        return toPoseFrame(best, params, sourceWidth, sourceHeight, timestampMs)
    }

    /**
     * Recorre las detecciones, filtra por confianza y arma las cajas en formato xyxy.
     *
     * Todas las coordenadas se llevan a píxeles del espacio de entrada del modelo
     * multiplicando por [PoseModelSpec.coordinateScale] (no-op si el modelo ya emite
     * píxeles), de modo que NMS y el des-letterboxing trabajan siempre en la misma unidad.
     * Las confianzas nunca se escalan.
     */
    internal fun decode(output: FloatArray): List<Detection> {
        val numDetections = output.size / spec.outputChannels
        val result = ArrayList<Detection>()
        val toPixels = spec.coordinateScale

        for (d in 0 until numDetections) {
            val score = output[spec.indexOf(PoseModelSpec.SCORE_CHANNEL, d, numDetections)]
            if (score < confidenceThreshold) continue

            val cx = output[spec.indexOf(0, d, numDetections)] * toPixels
            val cy = output[spec.indexOf(1, d, numDetections)] * toPixels
            val w = output[spec.indexOf(2, d, numDetections)] * toPixels
            val h = output[spec.indexOf(3, d, numDetections)] * toPixels

            val stride = PoseModelSpec.KEYPOINT_STRIDE
            val keypoints = FloatArray(spec.keypointCount * stride)
            for (k in 0 until spec.keypointCount) {
                val base = PoseModelSpec.FIRST_KEYPOINT_CHANNEL + k * stride
                keypoints[k * stride] = output[spec.indexOf(base, d, numDetections)] * toPixels
                keypoints[k * stride + 1] =
                    output[spec.indexOf(base + 1, d, numDetections)] * toPixels
                keypoints[k * stride + 2] = output[spec.indexOf(base + 2, d, numDetections)]
            }

            result.add(
                Detection(
                    score = score,
                    x1 = cx - w / 2f,
                    y1 = cy - h / 2f,
                    x2 = cx + w / 2f,
                    y2 = cy + h / 2f,
                    keypoints = keypoints
                )
            )
        }
        return result
    }

    /** Non-Maximum Suppression voraz. Devuelve los supervivientes ordenados por score desc. */
    internal fun nms(candidates: List<Detection>): List<Detection> {
        val sorted = candidates.sortedByDescending { it.score }
        val kept = ArrayList<Detection>()
        val suppressed = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val a = sorted[i]
            kept.add(a)
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                if (iou(a, sorted[j]) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return kept
    }

    private fun iou(a: Detection, b: Detection): Float {
        val interX1 = max(a.x1, b.x1)
        val interY1 = max(a.y1, b.y1)
        val interX2 = min(a.x2, b.x2)
        val interY2 = min(a.y2, b.y2)
        val interW = max(0f, interX2 - interX1)
        val interH = max(0f, interY2 - interY1)
        val interArea = interW * interH
        val areaA = max(0f, a.x2 - a.x1) * max(0f, a.y2 - a.y1)
        val areaB = max(0f, b.x2 - b.x1) * max(0f, b.y2 - b.y1)
        val union = areaA + areaB - interArea
        return if (union <= 0f) 0f else interArea / union
    }

    /**
     * Des-letterboxea y normaliza los keypoints de [detection] al espacio de la imagen
     * original.
     *
     * **No se clampea a `[0,1]`**: una articulación fuera de cuadro tiene que quedar fuera
     * de cuadro. Clampear la pegaría al borde, dibujando un hueso que salta al margen, y
     * —peor— disfrazaría un error de escala de coordenadas de esqueleto plausible.
     */
    private fun toPoseFrame(
        detection: Detection,
        params: LetterboxParams,
        sourceWidth: Int,
        sourceHeight: Int,
        timestampMs: Long
    ): PoseFrame {
        val stride = PoseModelSpec.KEYPOINT_STRIDE
        val keypoints = ArrayList<Keypoint>(mappedKeypoints)
        for (k in 0 until mappedKeypoints) {
            val kx = detection.keypoints[k * stride]
            val ky = detection.keypoints[k * stride + 1]
            val kConf = detection.keypoints[k * stride + 2]

            // Modelo → original: (m - pad) / scale; luego normalizar por el lado original.
            val origX = (kx - params.padX) / params.scale
            val origY = (ky - params.padY) / params.scale
            keypoints.add(
                Keypoint(
                    type = KeypointType.entriesByIndex[k],
                    x = origX / sourceWidth,
                    y = origY / sourceHeight,
                    confidence = kConf
                )
            )
        }

        return PoseFrame(
            keypoints = keypoints,
            score = detection.score,
            timestampMs = timestampMs,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight
        )
    }
}
