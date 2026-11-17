package com.steadypath.app.data.vision

import com.steadypath.app.domain.model.KeypointType

/**
 * Descripción declarativa del modelo de pose que usa la app.
 *
 * Es la **única fuente de verdad** del contrato del `.tflite`: nombre del asset, geometría
 * de entrada, normalización y layout de salida. [PosePreprocessor], [PosePostprocessor] y
 * [YoloPoseEstimator] derivan de aquí todas sus constantes, así que **cambiar de modelo se
 * reduce a editar [ACTIVE]** (o a proveer otro spec desde `VisionModule`): el pipeline no
 * se toca.
 *
 * `YoloPoseEstimator` contrasta este spec contra los tensores reales al inicializar, de
 * modo que un spec desactualizado falla rápido y con un mensaje que indica exactamente qué
 * campo corregir.
 */
data class PoseModelSpec(
    /** Nombre del archivo dentro de `app/src/main/assets/`. */
    val assetFileName: String,
    /** Lado de la entrada cuadrada del modelo, en píxeles (se letterboxea a [inputSize]²). */
    val inputSize: Int = DEFAULT_INPUT_SIZE,
    /** Keypoints por persona que emite el modelo (COCO = 17). */
    val keypointCount: Int = KeypointType.COUNT,
    /** Cómo mapear el byte del píxel `[0,255]` al float de entrada. */
    val normalization: Normalization = Normalization.UNIT,
    /** Orden de canales que espera el modelo. */
    val channelOrder: ChannelOrder = ChannelOrder.RGB,
    /** Disposición del tensor de salida crudo. */
    val outputLayout: OutputLayout = OutputLayout.CHANNEL_MAJOR,
    /** Escala en la que el modelo expresa las coordenadas de boxes y keypoints. */
    val coordinateSpace: CoordinateSpace = CoordinateSpace.PIXELS,
    /** Gris del padding del letterbox (114 = valor estándar de Ultralytics). */
    val padValue: Int = DEFAULT_PAD_VALUE,
    /** Score mínimo de persona para considerar una detección. */
    val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD,
    /** IoU por encima del cual NMS suprime una detección duplicada. */
    val iouThreshold: Float = DEFAULT_IOU_THRESHOLD,
    /**
     * Detecciones (anchors) que emite la cabeza del modelo. Por defecto se deriva de
     * [inputSize] con los strides 8/16/32 de YOLOv8 (640 → 8400).
     */
    val outputDetections: Int = anchorsFor(inputSize)
) {

    init {
        require(inputSize > 0 && inputSize % MAX_STRIDE == 0) {
            "inputSize debe ser un múltiplo positivo de $MAX_STRIDE; recibido $inputSize"
        }
        require(keypointCount > 0) { "keypointCount debe ser positivo; recibido $keypointCount" }
        require(outputDetections > 0) {
            "outputDetections debe ser positivo; recibido $outputDetections"
        }
    }

    /** `4` de box + `1` de score + `3` por keypoint (56 para COCO-17). */
    val outputChannels: Int = BOX_CHANNELS + SCORE_CHANNELS + keypointCount * KEYPOINT_STRIDE

    /** Cantidad de floats del tensor de salida aplanado. */
    val outputElementCount: Int = outputChannels * outputDetections

    /** Bytes del buffer de entrada (`float32` NHWC). */
    val inputByteCount: Int = inputSize * inputSize * INPUT_CHANNELS * Float.SIZE_BYTES

    val expectedInputShape: IntArray = intArrayOf(1, inputSize, inputSize, INPUT_CHANNELS)

    val expectedOutputShape: IntArray = when (outputLayout) {
        OutputLayout.CHANNEL_MAJOR -> intArrayOf(1, outputChannels, outputDetections)
        OutputLayout.DETECTION_MAJOR -> intArrayOf(1, outputDetections, outputChannels)
    }

    /**
     * Factor que lleva una coordenada de la salida cruda al espacio de píxeles de la entrada
     * del modelo. Con [CoordinateSpace.PIXELS] es `1` (no-op); con [CoordinateSpace.NORMALIZED],
     * [inputSize].
     */
    val coordinateScale: Float = when (coordinateSpace) {
        CoordinateSpace.PIXELS -> 1f
        CoordinateSpace.NORMALIZED -> inputSize.toFloat()
    }

    /**
     * Tabla `[0,255] → float` precalculada, para resolver la normalización con un lookup
     * por canal en vez de un multiply-add (el preprocesado toca ~1,2 M canales por frame).
     */
    fun normalizationTable(): FloatArray =
        FloatArray(256) { it * normalization.scale + normalization.offset }

    /**
     * Posición del canal [channel] de la detección [detection] dentro de la salida
     * aplanada, según [outputLayout]. [detectionCount] se pasa explícito porque el
     * postprocesador lo deduce del tamaño real del tensor.
     */
    fun indexOf(channel: Int, detection: Int, detectionCount: Int): Int = when (outputLayout) {
        OutputLayout.CHANNEL_MAJOR -> channel * detectionCount + detection
        OutputLayout.DETECTION_MAJOR -> detection * outputChannels + channel
    }

    /** Mapeo del byte del píxel `[0,255]` al float que espera el modelo. */
    enum class Normalization(val scale: Float, val offset: Float) {
        /** `p / 255` → `[0,1]`. Export por defecto de Ultralytics. */
        UNIT(1f / 255f, 0f),

        /** `p / 127.5 - 1` → `[-1,1]`. */
        SIGNED_UNIT(1f / 127.5f, -1f),

        /** Píxel crudo `[0,255]`. */
        RAW(1f, 0f)
    }

    /** Orden en que se escriben los canales de color en el buffer de entrada. */
    enum class ChannelOrder { RGB, BGR }

    /**
     * Disposición del tensor de salida:
     * - [CHANNEL_MAJOR]: `[1, canales, detecciones]` — export por defecto de YOLOv8.
     * - [DETECTION_MAJOR]: `[1, detecciones, canales]`.
     */
    enum class OutputLayout { CHANNEL_MAJOR, DETECTION_MAJOR }

    /**
     * Escala de las coordenadas (`cx, cy, w, h` y los `x, y` de cada keypoint) en la salida
     * cruda. **No afecta a los canales de confianza.**
     *
     * El export a TFLite de Ultralytics normaliza las coordenadas a `[0,1]` —a diferencia
     * del export a ONNX, que las deja en píxeles— para ganar estabilidad numérica bajo
     * cuantización int8. Si el esqueleto colapsa en la esquina superior izquierda, este es
     * el campo a revisar: el modelo emite [NORMALIZED] y el spec declara [PIXELS].
     */
    enum class CoordinateSpace { PIXELS, NORMALIZED }

    companion object {

        /**
         * Modelo de la tesis: student pruneado + fine-tuneado, cuantizado a INT8.
         *
         * Es el que tiene que terminar en producción. v3 reentrena sobre un dataset mixto y
         * corrige el esqueleto colapsado de v2 (ver `CLAUDE.md`).
         */
        val STUDENT_V3 = PoseModelSpec(
            assetFileName = "student_pruned_ft_deploy_v3_int8.tflite",
            // Verificado en dispositivo: la salida cruda da box=[0.46 0.28 0.53 0.84] y
            // keypoints en el mismo rango, no en 0..640.
            coordinateSpace = CoordinateSpace.NORMALIZED
        )

        /**
         * YOLOv8n-pose stock de Ultralytics, entrenado en COCO. **Andamio temporal.**
         *
         * Mismo contrato de tensores que [STUDENT_V3], así que el pipeline no cambia: sirve
         * de referencia de calidad y desbloquea el trabajo de UI y de comparación de
         * movimiento mientras el student se reentrena en paralelo. Pesa ~1,3 MB más.
         */
        val STOCK_YOLOV8N = PoseModelSpec(
            assetFileName = "yolov8n-pose_int8.tflite",
            coordinateSpace = CoordinateSpace.NORMALIZED
        )

        /**
         * Candidato a prueba (2026-10-07), asset `best_int8.tflite`. Contrato de tensores
         * verificado contra el `.tflite` real: entrada `[1,640,640,3]` float32, salida
         * `[1,56,8400]` float32 — mismo layout que [STUDENT_V3]/[STOCK_YOLOV8N].
         * `coordinateSpace` confirmado por inferencia sintética offline →
         * [CoordinateSpace.NORMALIZED].
         *
         * **Probado en dispositivo el 2026-10-07-08: no anduvo bien** (el usuario lo
         * descartó). No se registró en código el síntoma puntual — si se retoma este
         * candidato, repetir la prueba y anotar acá qué falló (esqueleto colapsado,
         * keypoints erráticos, etc.) antes de volver a activarlo.
         */
        val CANDIDATE_BEST_INT8 = PoseModelSpec(
            assetFileName = "best_int8.tflite",
            coordinateSpace = CoordinateSpace.NORMALIZED
        )

        /**
         * **Modelo activo.** Cambiar de modelo = apuntar esto a otro spec y dejar el
         * `.tflite` en `app/src/main/assets/` (los modelos no se versionan, ver
         * `.gitignore`). Si el export cambia geometría, normalización, layout o escala de
         * coordenadas, se ajustan los campos del spec y el resto del pipeline se adapta
         * solo. Ver [CoordinateSpace] si el esqueleto aparece pegado a una esquina.
         *
         * **[STUDENT_V3]** desde 2026-11-14. Antes (2026-10-08) se había vuelto a
         * [STOCK_YOLOV8N] porque [CANDIDATE_BEST_INT8] no funcionó bien en dispositivo.
         */
        val ACTIVE = STUDENT_V3

        const val DEFAULT_INPUT_SIZE = 640
        const val DEFAULT_PAD_VALUE = 114
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.25f
        const val DEFAULT_IOU_THRESHOLD = 0.45f

        /** RGB. */
        const val INPUT_CHANNELS = 3

        /** `cx, cy, w, h`. */
        const val BOX_CHANNELS = 4

        /** Score de la clase «persona». */
        const val SCORE_CHANNELS = 1

        /** `x, y, conf` por keypoint. */
        const val KEYPOINT_STRIDE = 3

        /** Canal del score de persona (justo después del box). */
        const val SCORE_CHANNEL = BOX_CHANNELS

        /** Primer canal de keypoints. */
        const val FIRST_KEYPOINT_CHANNEL = BOX_CHANNELS + SCORE_CHANNELS

        private const val MAX_STRIDE = 32

        /**
         * Anchors totales de una cabeza YOLOv8 (strides 8/16/32) para entrada cuadrada:
         * `(s/8)² + (s/16)² + (s/32)²`. Para 640 → 8400.
         */
        fun anchorsFor(inputSize: Int): Int {
            val p3 = inputSize / 8
            val p4 = inputSize / 16
            val p5 = inputSize / 32
            return p3 * p3 + p4 * p4 + p5 * p5
        }
    }
}
