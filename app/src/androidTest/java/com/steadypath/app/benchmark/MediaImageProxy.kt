package com.steadypath.app.benchmark

import android.annotation.SuppressLint
import android.graphics.ImageFormat
import android.graphics.Rect
import android.media.Image
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageInfo
import androidx.camera.core.ImageProxy
import androidx.camera.core.impl.TagBundle
import androidx.camera.core.impl.utils.ExifData

/**
 * [ImageProxy] sobre un frame YUV_420_888 que entrega `MediaCodec` al decodificar un video.
 *
 * Existe para que el benchmark entre por **el mismo método** que el analyzer de CameraX
 * (`PoseEstimator.estimate(ImageProxy, rotationDegrees)`): `toBitmap()` es la conversión
 * nativa de CameraX (libyuv) y sólo lee formato, tamaño y planos, así que el costo de
 * preprocesado medido es el de producción y no el de una conversión propia del test.
 *
 * La imagen es del codec (se libera con `releaseOutputBuffer`): [close] no hace nada.
 */
@SuppressLint("RestrictedApi")
class MediaImageProxy(
    private val image: Image,
    private val rotationDegrees: Int
) : ImageProxy {

    init {
        require(image.format == ImageFormat.YUV_420_888) {
            "El decoder entregó formato ${image.format}; se esperaba YUV_420_888"
        }
        // CameraX convierte desde el origen del plano: un crop corrido leería basura.
        require(image.cropRect.left == 0 && image.cropRect.top == 0) {
            "Crop del decoder fuera del origen (${image.cropRect}); no soportado"
        }
    }

    private val planes: Array<ImageProxy.PlaneProxy> = image.planes.map { plane ->
        object : ImageProxy.PlaneProxy {
            override fun getRowStride(): Int = plane.rowStride
            override fun getPixelStride(): Int = plane.pixelStride
            override fun getBuffer() = plane.buffer
        }
    }.toTypedArray()

    private var cropRect = Rect(image.cropRect)

    private val imageInfo = object : ImageInfo {
        override fun getTagBundle(): TagBundle = TagBundle.emptyBundle()
        override fun getTimestamp(): Long = image.timestamp
        override fun getRotationDegrees(): Int = rotationDegrees
        override fun populateExifData(exifBuilder: ExifData.Builder) = Unit
    }

    override fun close() = Unit

    override fun getCropRect(): Rect = Rect(cropRect)

    override fun setCropRect(rect: Rect?) {
        cropRect = rect?.let(::Rect) ?: Rect(0, 0, width, height)
    }

    override fun getFormat(): Int = ImageFormat.YUV_420_888

    // El tamaño útil es el crop: algunos decoders alinean el buffer (p. ej. 368 filas
    // para un video de 360) y esas filas extra no son imagen.
    override fun getWidth(): Int = image.cropRect.width()

    override fun getHeight(): Int = image.cropRect.height()

    override fun getPlanes(): Array<ImageProxy.PlaneProxy> = planes

    override fun getImageInfo(): ImageInfo = imageInfo

    @ExperimentalGetImage
    override fun getImage(): Image = image
}
