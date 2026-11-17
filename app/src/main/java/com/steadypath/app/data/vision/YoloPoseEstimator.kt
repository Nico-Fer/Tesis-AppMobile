package com.steadypath.app.data.vision

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.steadypath.app.BuildConfig
import com.steadypath.app.domain.model.PoseFrame
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementación de [PoseEstimator] sobre LiteRT (TensorFlow Lite).
 *
 * Carga desde assets (memory-mapped) el modelo que describe [PoseModelSpec], crea el
 * [Interpreter] con delegate de **GPU** y fallback a **CPU/XNNPACK**, y orquesta el pipeline
 * preprocesado → inferencia → postprocesado para cada frame.
 *
 * No hay constantes del modelo hardcodeadas acá: nombre del asset, formas esperadas y
 * tamaño de los buffers salen del [spec] inyectado, así que cambiar de modelo es cambiar
 * `PoseModelSpec.ACTIVE`.
 *
 * Singleton: un único intérprete para toda la app, compartido por conteo de referencias.
 * [acquire] es el **único** punto de inicialización y debe llamarse en un hilo de fondo;
 * [release] libera al llegar a cero usuarios. [estimate] está pensado para el hilo del
 * `Analyzer` de CameraX y descarta el frame (devuelve null) si el intérprete aún no existe
 * o ya fue liberado — un frame rezagado tras la liberación no debe recrear el modelo,
 * porque esa instancia quedaría huérfana y filtraría memoria nativa + delegate GPU.
 */
@Singleton
class YoloPoseEstimator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val spec: PoseModelSpec
) : PoseEstimator {

    private val preprocessor = PosePreprocessor(spec)
    private val postprocessor = PosePostprocessor(spec)

    private val lock = Any()

    @Volatile
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    /** Usuarios vivos del intérprete; protegido por [lock]. */
    private var usuarios = 0

    private val _estado = MutableStateFlow<EstadoEstimador>(EstadoEstimador.Inactivo)
    override val estado: StateFlow<EstadoEstimador> = _estado.asStateFlow()

    /**
     * Salida cruda reutilizada entre frames. Se lee como buffer plano (en vez de arrays
     * anidados), así el mismo código sirve para cualquier forma de salida.
     */
    private val outputBuffer: ByteBuffer =
        ByteBuffer.allocateDirect(spec.outputElementCount * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())

    /** Vista float del buffer anterior, creada una sola vez (comparten contenido). */
    private val outputFloatView = outputBuffer.asFloatBuffer()
    private val outputArray = FloatArray(spec.outputElementCount)

    private var frameCount = 0L

    override fun acquire() {
        synchronized(lock) {
            if (interpreter == null) ensureInitializedLocked()
            usuarios++
        }
    }

    override fun release() {
        synchronized(lock) {
            if (usuarios == 0) return
            usuarios--
            if (usuarios == 0) closeLocked()
        }
    }

    override fun estimate(image: ImageProxy, rotationDegrees: Int): PoseFrame? {
        // Sin warm-up completado (o ya liberado): se descarta el frame en vez de inicializar.
        if (interpreter == null) return null

        val pre = preprocessor.preprocess(image, rotationDegrees)

        val inferenceMs: Double
        // Mismo lock que close(): evita liberar el intérprete con una inferencia en vuelo.
        synchronized(lock) {
            val tflite = interpreter ?: return null
            val startNs = System.nanoTime()
            outputBuffer.rewind()
            tflite.run(pre.buffer, outputBuffer)
            outputFloatView.rewind()
            outputFloatView.get(outputArray)
            inferenceMs = (System.nanoTime() - startNs) / 1_000_000.0
        }

        val frame = postprocessor.process(
            output = outputArray,
            params = pre.params,
            sourceWidth = pre.sourceWidth,
            sourceHeight = pre.sourceHeight,
            timestampMs = System.currentTimeMillis()
        )

        frameCount++
        if (frameCount % LOG_EVERY_N_FRAMES == 0L) {
            logLatency(inferenceMs)
            // Solo texto en el log: ninguna versión guarda imágenes de la cámara.
            if (BuildConfig.DEBUG) {
                PoseDiagnostics.log(
                    spec = spec,
                    output = outputArray,
                    params = pre.params,
                    sourceWidth = pre.sourceWidth,
                    sourceHeight = pre.sourceHeight,
                    frame = frame
                )
            }
        }

        return frame
    }

    private fun closeLocked() {
        interpreter?.close()
        interpreter = null
        gpuDelegate?.close()
        gpuDelegate = null
        _estado.value = EstadoEstimador.Inactivo
    }

    /** Requiere [lock]. Publica Cargando → Listo, o Error y relanza si algo falla. */
    private fun ensureInitializedLocked() {
        _estado.value = EstadoEstimador.Cargando
        var created: Interpreter? = null
        try {
            created = createInterpreter()
            verifyContract(created)
            interpreter = created
            _estado.value = EstadoEstimador.Listo
        } catch (t: Throwable) {
            created?.close()
            gpuDelegate?.close()
            gpuDelegate = null
            _estado.value = EstadoEstimador.Error(
                t.message ?: "No se pudo cargar el modelo de pose."
            )
            throw t
        }
    }

    private fun createInterpreter(): Interpreter {
        val model = FileUtil.loadMappedFile(context, spec.assetFileName)
        val options = Interpreter.Options()

        val compatList = CompatibilityList()
        val usedGpu = try {
            if (compatList.isDelegateSupportedOnThisDevice) {
                val delegate = GpuDelegate()
                gpuDelegate = delegate
                options.addDelegate(delegate)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            fallBackToCpu(e)
        } catch (e: LinkageError) {
            // El delegate de GPU puede faltar a nivel de *clases*, no sólo fallar al crearse:
            // si el classpath no tiene `litert-gpu-api`, el constructor de GpuDelegate tira
            // NoClassDefFoundError. Es un Error, no un Exception, así que sin este catch el
            // fallback a CPU no ocurre y acquire() propaga: la pantalla queda sin esqueleto.
            fallBackToCpu(e)
        } finally {
            compatList.close()
        }

        if (!usedGpu) {
            options.setNumThreads(Runtime.getRuntime().availableProcessors().coerceAtMost(4))
            options.setUseXNNPACK(true)
        }

        Log.i(
            TAG,
            "Interpreter creado para '${spec.assetFileName}' " +
                "con delegate=${if (usedGpu) "GPU" else "CPU/XNNPACK"}"
        )
        return Interpreter(model, options)
    }

    /** Descarta el delegate de GPU y deja que [createInterpreter] siga por CPU/XNNPACK. */
    private fun fallBackToCpu(cause: Throwable): Boolean {
        Log.w(TAG, "No se pudo crear el delegate de GPU, usando CPU/XNNPACK", cause)
        gpuDelegate?.close()
        gpuDelegate = null
        return false
    }

    /**
     * Confirma en runtime que el modelo cargado coincide con el [spec]. Si el modelo se
     * reexporta con otra geometría, falla al inicializar con un mensaje que indica qué
     * campo de [PoseModelSpec] hay que ajustar (en vez de degradar en silencio).
     */
    private fun verifyContract(tflite: Interpreter) {
        val input = tflite.getInputTensor(0)
        val output = tflite.getOutputTensor(0)
        Log.i(
            TAG,
            "Contrato de '${spec.assetFileName}' → " +
                "input=${input.shape().joinToString()} (${input.dataType()}), " +
                "output=${output.shape().joinToString()} (${output.dataType()})"
        )

        check(input.shape().contentEquals(spec.expectedInputShape)) {
            shapeError("entrada", input.shape(), spec.expectedInputShape, "inputSize")
        }
        check(output.shape().contentEquals(spec.expectedOutputShape)) {
            shapeError(
                "salida",
                output.shape(),
                spec.expectedOutputShape,
                "keypointCount / outputDetections / outputLayout"
            )
        }
        check(input.dataType() == DataType.FLOAT32 && output.dataType() == DataType.FLOAT32) {
            "El pipeline sólo soporta E/S float32, pero '${spec.assetFileName}' declara " +
                "input=${input.dataType()} y output=${output.dataType()}. Reexportá el modelo " +
                "con entradas/salidas float32 (la cuantización interna int8 sí está soportada)."
        }
    }

    private fun shapeError(
        which: String,
        actual: IntArray,
        expected: IntArray,
        fields: String
    ): String =
        "Forma de $which inesperada en '${spec.assetFileName}': ${actual.joinToString()}; " +
            "PoseModelSpec espera ${expected.joinToString()}. " +
            "Ajustá $fields en PoseModelSpec.ACTIVE."

    private fun logLatency(inferenceMs: Double) {
        Log.d(
            TAG,
            "Inferencia: ${"%.1f".format(inferenceMs)} ms " +
                "(~${"%.1f".format(1000.0 / inferenceMs)} fps)"
        )
    }

    companion object {
        private const val TAG = "YoloPoseEstimator"
        private const val LOG_EVERY_N_FRAMES = 30L
    }
}
