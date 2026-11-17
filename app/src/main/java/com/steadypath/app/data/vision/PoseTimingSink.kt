package com.steadypath.app.data.vision

/**
 * Tiempos de una pasada del pipeline de pose sobre un frame, en milisegundos.
 *
 * [preprocessMs] incluye la conversión YUV→RGB de CameraX (`toBitmap`), la rotación y el
 * letterbox; [inferenceMs] es sólo `Interpreter.run` más la copia de la salida, que es lo
 * que reportaba el log `Inferencia: … ms`.
 */
data class PoseFrameTimings(
    val preprocessMs: Double,
    val inferenceMs: Double,
    val postprocessMs: Double
)

/**
 * Seam de telemetría por frame del estimador. Lo consume el benchmark instrumentado
 * (`androidTest/…/benchmark`) para separar el costo de cada etapa; en producción es no-op.
 */
fun interface PoseTimingSink {
    fun onFrame(timings: PoseFrameTimings)

    companion object {
        val NO_OP = PoseTimingSink { }
    }
}
