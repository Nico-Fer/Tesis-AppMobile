package com.steadypath.app.presentation.screens.calibration

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.BuildConfig
import com.steadypath.app.data.vision.EstadoEstimador
import com.steadypath.app.data.vision.PoseEstimator
import com.steadypath.app.data.vision.PoseEstimatorSession
import com.steadypath.app.domain.model.KeypointType
import com.steadypath.app.domain.model.PoseFrame
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.sqrt

/**
 * Encuadre en vivo antes de entrar a la cámara del ejercicio: sin esto, nada impedía arrancar
 * el ejercicio con el cuerpo fuera de cuadro, y la sesión se quedaba después en "Analizando…"
 * para siempre porque el comparador nunca llegaba a ver los ángulos que necesitaba.
 *
 * La referencia propia ya no se pide acá: la exige el detalle del ejercicio antes de llegar.
 * Acá además se ofrecía calibrar el rango, y quien acababa de grabar su referencia se
 * encontraba con un segundo pedido que no le agregaba nada, porque la amplitud ya sale de su
 * propio movimiento.
 */
@HiltViewModel
class PrepareSpaceViewModel @Inject constructor(
    private val poseEstimator: PoseEstimator,
    private val estimatorSession: PoseEstimatorSession,
    @ApplicationContext context: Context
) : ViewModel() {

    private val _pose = MutableStateFlow<PoseFrame?>(null)
    val pose: StateFlow<PoseFrame?> = _pose.asStateFlow()

    val estadoModelo: StateFlow<EstadoEstimador> get() = estimatorSession.estado

    private val _cuerpoVisible = MutableStateFlow(false)
    val cuerpoVisible: StateFlow<Boolean> = _cuerpoVisible.asStateFlow()

    private val _buenaIluminacion = MutableStateFlow(false)
    val buenaIluminacion: StateFlow<Boolean> = _buenaIluminacion.asStateFlow()

    // Sin acelerómetro (inexistente en la práctica en un teléfono Android) no hay forma de
    // medirlo: no bloquea.
    private val _camaraEstable = MutableStateFlow(true)
    val camaraEstable: StateFlow<Boolean> = _camaraEstable.asStateFlow()

    /** Cumple las tres condiciones a la vez: lo que habilita el botón de continuar. */
    val listoParaEmpezar: StateFlow<Boolean> get() = _listoParaEmpezar
    private val _listoParaEmpezar = MutableStateFlow(false)

    private var loadedExerciseId: String? = null

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    // TYPE_ACCELEROMETER (crudo, incluye gravedad) en vez de TYPE_LINEAR_ACCELERATION: este
    // último es una fusión por software que puede faltar según el fabricante, mientras que el
    // acelerómetro crudo es prácticamente universal. La gravedad se estima y se resta acá
    // mismo (alta-pasa simple) en vez de depender de que el sistema la calcule.
    private val acelerometro = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private var gravedadEstimada: FloatArray? = null

    /** Promedio móvil exponencial de la magnitud de aceleración: suaviza el ruido del sensor. */
    private var magnitudSuavizada = 0f

    private var eventosSensor = 0L

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            var gravedad = gravedadEstimada
            if (gravedad == null) {
                // Primer evento: arranca la estimación de gravedad en la lectura cruda en vez
                // de en cero, para no leer el peso del propio teléfono como "movimiento" en
                // las primeras muestras mientras el filtro converge.
                gravedad = event.values.copyOf()
                gravedadEstimada = gravedad
            } else {
                for (i in 0..2) {
                    gravedad[i] = ALFA_GRAVEDAD * gravedad[i] + (1 - ALFA_GRAVEDAD) * event.values[i]
                }
            }
            val linealX = event.values[0] - gravedad[0]
            val linealY = event.values[1] - gravedad[1]
            val linealZ = event.values[2] - gravedad[2]
            val magnitud = sqrt(linealX * linealX + linealY * linealY + linealZ * linealZ)
            magnitudSuavizada = ALFA_SUAVIZADO * magnitud + (1 - ALFA_SUAVIZADO) * magnitudSuavizada
            val estable = magnitudSuavizada < UMBRAL_INESTABILIDAD
            if (_camaraEstable.value != estable) _camaraEstable.value = estable

            eventosSensor++
            if (BuildConfig.DEBUG && eventosSensor % LOG_EVERY_N_EVENTOS == 0L) {
                FramingDiagnostics.log(
                    lumaPromedio = ultimaLuma,
                    umbralLuz = UMBRAL_ILUMINACION,
                    magnitudSuavizada = magnitudSuavizada,
                    umbralMovimiento = UMBRAL_INESTABILIDAD,
                    cuerpoVisible = _cuerpoVisible.value,
                    buenaIluminacion = _buenaIluminacion.value,
                    camaraEstable = estable
                )
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    /** Última luma medida, solo para el log de diagnóstico (ver [sensorListener]). */
    private var ultimaLuma = 0f

    init {
        if (acelerometro != null) {
            _camaraEstable.value = false
            sensorManager?.registerListener(
                sensorListener,
                acelerometro,
                SensorManager.SENSOR_DELAY_UI
            )
        }
        viewModelScope.launch {
            combine(_cuerpoVisible, _buenaIluminacion, _camaraEstable) { cuerpo, luz, estable ->
                cuerpo && luz && estable
            }.collect { _listoParaEmpezar.value = it }
        }
    }

    fun load(exerciseId: String) {
        if (loadedExerciseId == exerciseId) return
        loadedExerciseId = exerciseId
        estimatorSession.start(viewModelScope)
    }

    /** Se invoca desde el hilo del analyzer de CameraX; ver [PoseEstimator.estimate]. */
    fun onFrame(image: ImageProxy) {
        try {
            val luma = calcularIluminacionPromedio(image)
            ultimaLuma = luma
            _buenaIluminacion.value = luma >= UMBRAL_ILUMINACION
            val frame = poseEstimator.estimate(image, image.imageInfo.rotationDegrees)
            _pose.value = frame
            _cuerpoVisible.value = frame != null && esCuerpoCompletoVisible(frame)
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando frame", e)
        } finally {
            image.close()
        }
    }

    override fun onCleared() {
        super.onCleared()
        sensorManager?.unregisterListener(sensorListener)
        estimatorSession.stop()
    }

    /**
     * Brillo promedio del plano Y (luma) de un frame YUV_420_888, muestreado cada
     * [MUESTREO_PIXELS] bytes en vez de todos: a esta cadencia de cámara, promediar el
     * cuadro completo en cada frame es gasto de más por una métrica que solo necesita ser
     * aproximada.
     */
    private fun calcularIluminacionPromedio(image: ImageProxy): Float {
        val buffer = image.planes[0].buffer
        val total = buffer.remaining()
        if (total == 0) return 0f
        var suma = 0L
        var cuenta = 0
        var i = 0
        while (i < total) {
            suma += buffer.get(i).toInt() and 0xFF
            cuenta++
            i += MUESTREO_PIXELS
        }
        return suma.toFloat() / cuenta
    }

    private companion object {
        const val TAG = "PrepareSpaceViewModel"
        const val CONFIANZA_MINIMA = 0.5f

        /** Brillo [0,255] del plano Y por debajo del cual se considera el ambiente oscuro. */
        const val UMBRAL_ILUMINACION = 70f
        const val MUESTREO_PIXELS = 37

        /** Suavizado del acelerómetro y techo de magnitud (m/s²) para considerar "estable". */
        const val ALFA_SUAVIZADO = 0.3f
        const val UMBRAL_INESTABILIDAD = 0.6f

        /** Alta-pasa que separa gravedad de movimiento (ver `sensorListener`). */
        const val ALFA_GRAVEDAD = 0.8f

        /** Cadencia del log de diagnóstico de encuadre (SENSOR_DELAY_UI ≈ 60Hz). */
        const val LOG_EVERY_N_EVENTOS = 30L

        val KEYPOINTS_CUERPO_COMPLETO = listOf(
            KeypointType.LEFT_SHOULDER, KeypointType.RIGHT_SHOULDER,
            KeypointType.LEFT_HIP, KeypointType.RIGHT_HIP,
            KeypointType.LEFT_KNEE, KeypointType.RIGHT_KNEE,
            KeypointType.LEFT_ANKLE, KeypointType.RIGHT_ANKLE
        )

        /**
         * Encuadre aceptable: hombros, caderas, rodillas y tobillos detectados con confianza
         * y dentro del cuadro. No exige precisión clínica, solo que el modelo vea el cuerpo
         * completo antes de habilitar el ejercicio.
         */
        fun esCuerpoCompletoVisible(frame: PoseFrame): Boolean {
            val porTipo = frame.keypoints.associateBy { it.type }
            return KEYPOINTS_CUERPO_COMPLETO.all { tipo ->
                val kp = porTipo[tipo]
                kp != null && kp.confidence >= CONFIANZA_MINIMA &&
                    kp.x in 0f..1f && kp.y in 0f..1f
            }
        }
    }
}
