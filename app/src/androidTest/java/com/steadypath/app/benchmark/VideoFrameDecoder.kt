package com.steadypath.app.benchmark

import android.content.res.AssetFileDescriptor
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.Closeable

/**
 * Decodifica la pista de video de un clip a frames YUV_420_888 (`Image`), en orden de
 * presentación, con `MediaCodec` síncrono.
 *
 * Se prefiere el decoder **por software** de la plataforma: entrega `getOutputImage` en
 * formato flexible en todos los fabricantes (algunos decoders por hardware usan layouts
 * propietarios), y como H.264 es bit-exacto por norma los píxeles son los mismos en todos
 * los dispositivos. El costo de decodificar **no** entra en ninguna medición.
 *
 * Los clips se generan sin B-frames (ver `tools/benchmark/preparar_clips.py`), así que el
 * orden de decodificación coincide con el de presentación.
 */
class VideoFrameDecoder(private val afd: AssetFileDescriptor) : Closeable {

    private val extractor = MediaExtractor().apply {
        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
    }
    private val track: Int = (0 until extractor.trackCount).firstOrNull { i ->
        extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
    } ?: error("El clip no tiene pista de video")
    private val format: MediaFormat = extractor.getTrackFormat(track)
    private val mime: String = format.getString(MediaFormat.KEY_MIME)!!

    init {
        extractor.selectTrack(track)
    }

    /** Timestamps de presentación (µs) de todos los frames, sin decodificar. */
    fun presentationTimesUs(): LongArray {
        val times = ArrayList<Long>()
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        while (extractor.sampleTime >= 0) {
            times += extractor.sampleTime
            extractor.advance()
        }
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        return times.sorted().toLongArray()
    }

    /** Nombre del codec que se va a usar (queda registrado en el resultado). */
    val codecName: String by lazy { pickDecoderName() }

    /**
     * Recorre todos los frames. [onFrame] recibe el índice de presentación, el PTS en µs y
     * la imagen, que sólo es válida durante la llamada.
     */
    fun forEachFrame(onFrame: (index: Int, ptsUs: Long, image: Image) -> Unit) {
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val codec = MediaCodec.createByCodecName(codecName)
        try {
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            codec.configure(format, null, null, 0)
            codec.start()
            drain(codec, onFrame)
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private fun drain(codec: MediaCodec, onFrame: (Int, Long, Image) -> Unit) {
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var index = 0
        while (true) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (outIndex < 0) continue // formato cambiado o sin salida todavía
            val endOfStream = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            if (info.size > 0) {
                val image = codec.getOutputImage(outIndex)
                    ?: error("El decoder '$codecName' no entregó Image para el frame $index")
                onFrame(index++, info.presentationTimeUs, image)
            }
            codec.releaseOutputBuffer(outIndex, false)
            if (endOfStream) return
        }
    }

    private fun pickDecoderName(): String {
        val candidates = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
            !info.isEncoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) } &&
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible in
                info.getCapabilitiesForType(mime).colorFormats
        }
        return (candidates.firstOrNull { it.isSoftwareOnly } ?: candidates.firstOrNull())?.name
            ?: error("Sin decoder YUV420 flexible para $mime en este dispositivo")
    }

    override fun close() {
        extractor.release()
        afd.close()
    }

    private companion object {
        const val TIMEOUT_US = 10_000L
    }
}
