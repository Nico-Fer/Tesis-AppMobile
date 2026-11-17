package com.steadypath.app.domain.model

/**
 * Resultado de la estimación de pose para un único frame de cámara.
 *
 * Las coordenadas de cada [Keypoint] están **normalizadas respecto del espacio de la imagen
 * original** (ya des-letterboxeadas), de forma que la capa de UI pueda dibujar el esqueleto
 * independientemente del tamaño del visor. Es un modelo de dominio puro: no depende de tipos
 * de Android ni de TensorFlow Lite.
 */
data class PoseFrame(
    val keypoints: List<Keypoint>,
    /** Confianza de la persona detectada (score del bounding box), en [0,1]. */
    val score: Float,
    /** Marca de tiempo de captura del frame, en milisegundos. */
    val timestampMs: Long,
    /** Ancho de la imagen original (sin letterbox), en píxeles. */
    val sourceWidth: Int,
    /** Alto de la imagen original (sin letterbox), en píxeles. */
    val sourceHeight: Int
)

/**
 * Punto clave anatómico detectado.
 *
 * [x] e [y] están normalizados sobre la imagen original: `0` es el borde izquierdo/superior
 * y `1` el derecho/inferior. **Pueden caer fuera de `[0,1]`** cuando la articulación está
 * fuera de cuadro (el modelo extrapola la posición de un miembro ocluido), así que quien
 * dibuje debe recortar en vez de asumir el rango. [confidence] sí está en `[0,1]` y es el
 * criterio para descartar un keypoint poco fiable.
 */
data class Keypoint(
    val type: KeypointType,
    val x: Float,
    val y: Float,
    val confidence: Float
)

/**
 * Los 17 keypoints del formato COCO, en el orden exacto en que YOLOv8-Pose los emite.
 * El orden es significativo: el índice ordinal coincide con el índice del keypoint en la
 * salida del modelo.
 */
enum class KeypointType {
    NOSE,
    LEFT_EYE,
    RIGHT_EYE,
    LEFT_EAR,
    RIGHT_EAR,
    LEFT_SHOULDER,
    RIGHT_SHOULDER,
    LEFT_ELBOW,
    RIGHT_ELBOW,
    LEFT_WRIST,
    RIGHT_WRIST,
    LEFT_HIP,
    RIGHT_HIP,
    LEFT_KNEE,
    RIGHT_KNEE,
    LEFT_ANKLE,
    RIGHT_ANKLE;

    companion object {
        /** Cantidad de keypoints por persona (COCO). */
        const val COUNT = 17

        /** Lookup por índice ordinal, cacheado para evitar realocar en el hot path. */
        val entriesByIndex: List<KeypointType> = entries
    }
}
