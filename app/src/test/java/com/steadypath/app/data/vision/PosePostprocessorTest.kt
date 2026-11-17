package com.steadypath.app.data.vision

import com.steadypath.app.domain.model.KeypointType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests JVM puros del decodificador de YOLOv8-Pose. Construyen una salida sintética a
 * partir del [PoseModelSpec] (sin constantes duplicadas) para verificar el indexado por
 * layout, el filtro por confianza, NMS, la selección de persona principal y el
 * des-letterboxing — sin dispositivo.
 */
class PosePostprocessorTest {

    /**
     * Fixture deliberadamente **independiente** de [PoseModelSpec.ACTIVE] en lo que hace al
     * espacio de coordenadas: estos tests arman la salida sintética en píxeles del espacio
     * de modelo, así que lo fijan en vez de heredarlo del modelo activo — que cambia cada
     * vez que se reexporta el `.tflite`.
     */
    private val spec = PoseModelSpec.ACTIVE.copy(
        coordinateSpace = PoseModelSpec.CoordinateSpace.PIXELS
    )
    private val numDetections = 3

    // Imagen original 1280×720 metida en 640×640 con letterbox:
    // scale = min(640/1280, 640/720) = 0.5 ; padX = 0 ; padY = (640 - 360)/2 = 140
    private val letterbox = LetterboxParams(scale = 0.5f, padX = 0f, padY = 140f)
    private val sourceWidth = 1280
    private val sourceHeight = 720

    /** Escribe el canal [channel] de la detección [det] respetando el layout del spec. */
    private fun FloatArray.set(spec: PoseModelSpec, channel: Int, det: Int, value: Float) {
        this[spec.indexOf(channel, det, numDetections)] = value
    }

    /** Detección base: box centrado + nariz en (320,200) del espacio de modelo. */
    private fun buildOutput(spec: PoseModelSpec = this.spec): FloatArray {
        val out = FloatArray(spec.outputChannels * numDetections)

        // Detección 0 — persona principal (score alto).
        out.set(spec, 0, 0, 320f) // cx
        out.set(spec, 1, 0, 320f) // cy
        out.set(spec, 2, 0, 200f) // w
        out.set(spec, 3, 0, 400f) // h
        out.set(spec, 4, 0, 0.90f) // score
        // Nariz (keypoint 0) en (320, 200), conf 0.9.
        out.set(spec, 5, 0, 320f)
        out.set(spec, 6, 0, 200f)
        out.set(spec, 7, 0, 0.90f)

        // Detección 1 — duplicado casi idéntico (IoU alto) con score menor → NMS la descarta.
        out.set(spec, 0, 1, 325f)
        out.set(spec, 1, 1, 320f)
        out.set(spec, 2, 1, 200f)
        out.set(spec, 3, 1, 400f)
        out.set(spec, 4, 1, 0.80f)

        // Detección 2 — score bajo → filtrada por confianza.
        out.set(spec, 0, 2, 100f)
        out.set(spec, 1, 2, 100f)
        out.set(spec, 2, 2, 50f)
        out.set(spec, 3, 2, 50f)
        out.set(spec, 4, 2, 0.10f)

        return out
    }

    @Test
    fun `filtra por confianza y descarta duplicados con NMS`() {
        val post = PosePostprocessor(spec)
        val out = buildOutput()

        // El filtro deja 2 candidatas (0.90 y 0.80); la de 0.10 se descarta.
        val candidates = post.decode(out)
        assertEquals(2, candidates.size)

        // NMS deja solo la principal (la duplicada se suprime por IoU alto).
        val survivors = post.nms(candidates)
        assertEquals(1, survivors.size)
        assertEquals(0.90f, survivors.first().score, 1e-4f)
    }

    @Test
    fun `selecciona la persona principal y des-letterboxea los keypoints`() {
        val post = PosePostprocessor(spec)

        val frame = post.process(
            output = buildOutput(),
            params = letterbox,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            timestampMs = 123L
        )

        assertNotNull(frame)
        requireNotNull(frame)
        assertEquals(0.90f, frame.score, 1e-4f)
        assertEquals(KeypointType.COUNT, frame.keypoints.size)
        assertEquals(123L, frame.timestampMs)

        // Nariz: modelo (320,200) → original ((320-0)/0.5, (200-140)/0.5) = (640, 120)
        // → normalizado (640/1280, 120/720) = (0.5, 0.1667).
        val nose = frame.keypoints[KeypointType.NOSE.ordinal]
        assertEquals(0.5f, nose.x, 1e-3f)
        assertEquals(0.1667f, nose.y, 1e-3f)
        assertEquals(0.90f, nose.confidence, 1e-4f)
    }

    @Test
    fun `devuelve null cuando ninguna deteccion supera el umbral`() {
        val post = PosePostprocessor(spec, confidenceThreshold = 0.95f)

        val frame = post.process(
            output = buildOutput(),
            params = letterbox,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            timestampMs = 0L
        )

        assertNull(frame)
    }

    @Test
    fun `decodifica igual una salida transpuesta cambiando solo el spec`() {
        val transposed = spec.copy(outputLayout = PoseModelSpec.OutputLayout.DETECTION_MAJOR)
        val post = PosePostprocessor(transposed)

        val frame = post.process(
            output = buildOutput(transposed),
            params = letterbox,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            timestampMs = 123L
        )

        requireNotNull(frame)
        assertEquals(0.90f, frame.score, 1e-4f)
        val nose = frame.keypoints[KeypointType.NOSE.ordinal]
        assertEquals(0.5f, nose.x, 1e-3f)
        assertEquals(0.1667f, nose.y, 1e-3f)
    }

    @Test
    fun `decodifica igual una salida normalizada cambiando solo el spec`() {
        // El export a TFLite de Ultralytics emite las coordenadas en [0,1] del tensor de
        // entrada en vez de píxeles. Declararlo en el spec tiene que dar el mismo resultado.
        val normalized = spec.copy(
            coordinateSpace = PoseModelSpec.CoordinateSpace.NORMALIZED
        )
        val post = PosePostprocessor(normalized)

        // Misma salida que el resto de los tests, con las coordenadas divididas por 640.
        val out = buildOutput(normalized)
        for (d in 0 until numDetections) {
            for (channel in coordinateChannels(normalized)) {
                val i = normalized.indexOf(channel, d, numDetections)
                out[i] = out[i] / normalized.inputSize
            }
        }

        val frame = post.process(
            output = out,
            params = letterbox,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            timestampMs = 123L
        )

        requireNotNull(frame)
        assertEquals(0.90f, frame.score, 1e-4f)
        val nose = frame.keypoints[KeypointType.NOSE.ordinal]
        assertEquals(0.5f, nose.x, 1e-3f)
        assertEquals(0.1667f, nose.y, 1e-3f)
        // La confianza no se escala: sigue siendo la probabilidad cruda del canal.
        assertEquals(0.90f, nose.confidence, 1e-4f)
    }

    @Test
    fun `sin clampear, un keypoint fuera de cuadro queda fuera de 0 a 1`() {
        val post = PosePostprocessor(spec)
        val out = buildOutput()
        // Nariz a la izquierda del área útil del letterbox → x original negativo.
        out.set(spec, 5, 0, -160f)

        val frame = post.process(
            output = out,
            params = letterbox,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight,
            timestampMs = 0L
        )

        requireNotNull(frame)
        // (-160 - 0) / 0.5 = -320 → -320/1280 = -0.25, no 0.
        assertEquals(-0.25f, frame.keypoints[KeypointType.NOSE.ordinal].x, 1e-3f)
    }

    /** Canales que llevan coordenadas: los 4 del box + los `x, y` de cada keypoint. */
    private fun coordinateChannels(spec: PoseModelSpec): List<Int> {
        val stride = PoseModelSpec.KEYPOINT_STRIDE
        val boxChannels = (0 until PoseModelSpec.BOX_CHANNELS).toList()
        val keypointChannels = (0 until spec.keypointCount).flatMap { k ->
            val base = PoseModelSpec.FIRST_KEYPOINT_CHANNEL + k * stride
            listOf(base, base + 1)
        }
        return boxChannels + keypointChannels
    }

    @Test
    fun `el spec deriva canales y anchors de la geometria del modelo`() {
        // COCO-17 → 4 box + 1 score + 17×3 = 56 canales; 640 → 8400 anchors.
        assertEquals(56, spec.outputChannels)
        assertEquals(8400, spec.outputDetections)
        assertEquals(56 * 8400, spec.outputElementCount)

        // Una entrada de 320 reduce los anchors a (40² + 20² + 10²) = 2100 sin tocar código.
        val small = PoseModelSpec(assetFileName = "otro.tflite", inputSize = 320)
        assertEquals(2100, small.outputDetections)
        assertEquals(56, small.outputChannels)
        assertEquals(
            listOf(1, 320, 320, 3),
            small.expectedInputShape.toList()
        )
    }
}
