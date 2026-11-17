package com.steadypath.app.domain.comparison

import com.steadypath.app.domain.model.PoseFrame
import kotlin.math.atan2

/**
 * Transforma un [PoseFrame] (keypoints) en un [AngleFrame] (vector de ángulos articulares)
 * según la [ExerciseDefinition] del ejercicio.
 *
 * Invariante crítica: la referencia y la captura en vivo deben pasar por exactamente este
 * mismo pipeline (mismas definiciones, mismo orden, radianes). La referencia cargada es la
 * fuente autoritativa de las definiciones, así que la invariante se cumple por construcción.
 */
interface AngleExtractor {
    fun extract(frame: PoseFrame, definition: ExerciseDefinition): AngleFrame
}

/**
 * Implementación por defecto: ángulo signado en el plano de imagen.
 *
 * Para cada [AngleDefinition] calcula `atan2(cross, dot)` entre los vectores (A−vértice) y
 * (C−vértice), resultado en (−π, π]. Los keypoints vienen normalizados por lado
 * (x/ancho, y/alto), lo que deforma los ángulos cuando la imagen no es cuadrada: los
 * vectores se reescalan a píxeles con `sourceWidth`/`sourceHeight` antes de medir.
 *
 * El ángulo es signado: una ejecución espejada (usuario de perfil opuesto a la referencia)
 * invierte el signo. Punto de decisión documentado — hoy se asume misma orientación de
 * cámara entre referencia y ejecución.
 */
class DefaultAngleExtractor(
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD
) : AngleExtractor {

    override fun extract(frame: PoseFrame, definition: ExerciseDefinition): AngleFrame {
        val d = definition.dimensions
        val angles = FloatArray(d)
        val valid = BooleanArray(d)
        val keypoints = frame.keypoints
        val count = keypoints.size

        for (k in 0 until d) {
            val def = definition.angleDefinitions[k]
            val ia = def.pointA.ordinal
            val iv = def.vertex.ordinal
            val ic = def.pointC.ordinal
            if (ia >= count || iv >= count || ic >= count) continue

            val a = keypoints[ia]
            val v = keypoints[iv]
            val c = keypoints[ic]
            if (a.confidence < confidenceThreshold ||
                v.confidence < confidenceThreshold ||
                c.confidence < confidenceThreshold
            ) {
                continue
            }

            val ax = (a.x - v.x) * frame.sourceWidth
            val ay = (a.y - v.y) * frame.sourceHeight
            val cx = (c.x - v.x) * frame.sourceWidth
            val cy = (c.y - v.y) * frame.sourceHeight

            // Vector de largo cero → ángulo indefinido (keypoints superpuestos).
            if ((ax == 0f && ay == 0f) || (cx == 0f && cy == 0f)) continue

            angles[k] = atan2(ax * cy - ay * cx, ax * cx + ay * cy)
            valid[k] = true
        }

        return AngleFrame(angles = angles, valid = valid, timestampMs = frame.timestampMs)
    }

    companion object {
        // TODO: calibrar con datos — umbral heredado del filtro de dibujo del overlay.
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.5f
    }
}
