package com.steadypath.app.data.vision

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Convierte un frame de CameraX ([ImageProxy], YUV_420_888) en el buffer de entrada del
 * modelo: lo orienta según la rotación, lo escala al cuadrado que pide el modelo con
 * **letterbox** (padding manteniendo aspect ratio) y lo normaliza a float32 en layout NHWC.
 *
 * Tamaño, normalización, orden de canales y color de padding salen de [PoseModelSpec], así
 * que un modelo con otra geometría no requiere tocar esta clase.
 *
 * Reutiliza el bitmap destino, el array de píxeles y el [ByteBuffer] entre frames para no
 * realocar en el hot path. **No es thread-safe**: está pensado para usarse desde el único
 * hilo del `Analyzer` de CameraX.
 */
class PosePreprocessor(
    private val spec: PoseModelSpec = PoseModelSpec.ACTIVE
) {

    private val inputSize = spec.inputSize

    /** Bitmap destino [inputSize]² reutilizado. */
    private val modelBitmap = Bitmap.createBitmap(inputSize, inputSize, Bitmap.Config.ARGB_8888)
    private val modelCanvas = Canvas(modelBitmap)
    private val pixels = IntArray(inputSize * inputSize)
    private val filterPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Buffer de entrada: 1×[inputSize]×[inputSize]×3 float32. */
    private val inputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(spec.inputByteCount).order(ByteOrder.nativeOrder())

    /** Lookup `[0,255] → float` de la normalización configurada en el spec. */
    private val normalizationTable = spec.normalizationTable()

    private val padColor = Color.rgb(spec.padValue, spec.padValue, spec.padValue)

    private val rotationMatrix = Matrix()

    /** Resultado del preprocesado. [buffer] se reutiliza entre llamadas. */
    data class Result(
        val buffer: ByteBuffer,
        val params: LetterboxParams,
        val sourceWidth: Int,
        val sourceHeight: Int
    )

    fun preprocess(image: ImageProxy, rotationDegrees: Int): Result {
        val source = image.toUprightBitmap(rotationDegrees)

        val srcW = source.width
        val srcH = source.height
        val scale = min(inputSize.toFloat() / srcW, inputSize.toFloat() / srcH)
        val scaledW = (srcW * scale).roundToInt()
        val scaledH = (srcH * scale).roundToInt()
        val padX = (inputSize - scaledW) / 2f
        val padY = (inputSize - scaledH) / 2f

        // Letterbox: fondo gris de relleno + imagen escalada centrada.
        modelCanvas.drawColor(padColor)
        val dst = Rect(
            padX.roundToInt(),
            padY.roundToInt(),
            padX.roundToInt() + scaledW,
            padY.roundToInt() + scaledH
        )
        modelCanvas.drawBitmap(source, null, dst, filterPaint)

        fillBuffer()

        return Result(
            buffer = inputBuffer,
            params = LetterboxParams(scale = scale, padX = padX, padY = padY),
            sourceWidth = srcW,
            sourceHeight = srcH
        )
    }

    /** Vuelca [modelBitmap] al [inputBuffer] como float32 normalizado, NHWC. */
    private fun fillBuffer() {
        modelBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        inputBuffer.rewind()
        val floatView = inputBuffer.asFloatBuffer()
        val table = normalizationTable

        // El orden se resuelve fuera del bucle: son ~1,2 M canales por frame.
        if (spec.channelOrder == PoseModelSpec.ChannelOrder.RGB) {
            for (pixel in pixels) {
                floatView.put(table[(pixel shr 16) and 0xFF]) // R
                floatView.put(table[(pixel shr 8) and 0xFF])  // G
                floatView.put(table[pixel and 0xFF])          // B
            }
        } else {
            for (pixel in pixels) {
                floatView.put(table[pixel and 0xFF])          // B
                floatView.put(table[(pixel shr 8) and 0xFF])  // G
                floatView.put(table[(pixel shr 16) and 0xFF]) // R
            }
        }
        inputBuffer.rewind()
    }

    /** Convierte el frame a un bitmap RGB ya rotado a su orientación vertical. */
    private fun ImageProxy.toUprightBitmap(rotationDegrees: Int): Bitmap {
        val raw = toBitmap()
        if (rotationDegrees == 0) return raw
        rotationMatrix.reset()
        rotationMatrix.postRotate(rotationDegrees.toFloat())
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, rotationMatrix, true)
    }
}
