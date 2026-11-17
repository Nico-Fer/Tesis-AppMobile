package com.steadypath.app.data.vision

import androidx.camera.core.ImageProxy
import com.steadypath.app.domain.model.PoseFrame
import kotlinx.coroutines.flow.StateFlow

/** Estado observable del modelo, para que la UI distinga "cargando" de "falló" de "sin pose". */
sealed interface EstadoEstimador {
    data object Inactivo : EstadoEstimador
    data object Cargando : EstadoEstimador
    data object Listo : EstadoEstimador
    data class Error(val mensaje: String) : EstadoEstimador
}

/**
 * Estimador de pose inyectable. Encapsula todo el pipeline de inferencia (preprocesado,
 * ejecución del modelo TFLite y postprocesado) detrás de una interfaz testeable, de forma
 * que la capa de comparación y el ViewModel no dependan de TensorFlow Lite ni de CameraX.
 *
 * Vive en `data` (y no en `domain`) porque su firma usa [ImageProxy] de CameraX.
 *
 * El ciclo de vida es por **conteo de referencias**: varias pantallas vivas a la vez (la
 * de encuadre en el back stack mientras se calibra, por ejemplo) comparten el mismo
 * intérprete, y se libera recién cuando la última lo suelta. Usar [PoseEstimatorSession]
 * desde los ViewModels en vez de llamar a [acquire]/[release] a mano.
 */
interface PoseEstimator {

    val estado: StateFlow<EstadoEstimador>

    /**
     * Ejecuta la estimación de pose sobre un frame de cámara de forma **síncrona**.
     *
     * Está pensado para invocarse desde el `Analyzer` de CameraX (hilo de análisis, fuera
     * del main thread). El llamador es responsable de cerrar el [image] tras esta llamada.
     *
     * @param image frame en formato YUV_420_888.
     * @param rotationDegrees rotación del frame (`imageInfo.rotationDegrees`) para orientar
     *   la imagen antes de inferir.
     * @return el [PoseFrame] de la persona principal, o `null` si no se detectó ninguna
     *   persona por encima del umbral de confianza (o el modelo no está cargado).
     */
    fun estimate(image: ImageProxy, rotationDegrees: Int): PoseFrame?

    /**
     * Registra un usuario del modelo y, si es el primero, lo inicializa. Bloqueante: debe
     * llamarse en un hilo de fondo. Si la inicialización falla, propaga la excepción y no
     * cuenta como adquirido.
     */
    fun acquire()

    /** Suelta un usuario; al llegar a cero libera intérprete y delegate. Idempotente en cero. */
    fun release()
}
