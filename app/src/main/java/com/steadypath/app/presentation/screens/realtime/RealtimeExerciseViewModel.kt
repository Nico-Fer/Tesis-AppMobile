package com.steadypath.app.presentation.screens.realtime

import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import com.steadypath.app.domain.comparison.ReferenceProvenance
import com.steadypath.app.domain.repository.ReferenceRepository
import com.steadypath.app.domain.repository.SessionCameraRepository
import androidx.lifecycle.viewModelScope
import com.steadypath.app.data.vision.EstadoEstimador
import com.steadypath.app.data.vision.PoseEstimator
import com.steadypath.app.data.vision.PoseEstimatorSession
import com.steadypath.app.domain.comparison.ComparisonResult
import com.steadypath.app.domain.comparison.MovementComparator
import com.steadypath.app.domain.comparison.MovementStatus
import com.steadypath.app.domain.model.PoseFrame
import com.steadypath.app.presentation.session.RoutineSessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

sealed interface RealtimeUiState {
    data object Idle : RealtimeUiState
    data object CameraPermissionRequired : RealtimeUiState
    data class Active(
        val exerciseName: String,
        val repCount: Int = 0,
        val targetReps: Int = 10,
        val currentSeries: Int = 1,
        val totalSeries: Int = 1,
        /** false = preparación/descanso: el conteo está pausado hasta tocar Comenzar. */
        val seriesInProgress: Boolean = false,
        val postureStatus: PostureStatus = PostureStatus.Unknown,
        val feedbackMessage: String = "",
        /** Reps correctas consecutivas; alimenta el conteo hablado. */
        val goodStreak: Int = 0,
        /** Anuncio de fin de serie para el TTS; null cuando no hay nada que anunciar. */
        val seriesAnnouncement: String? = null
    ) : RealtimeUiState
    data class Completed(val totalReps: Int, val durationSeconds: Int) : RealtimeUiState
    data class Error(val message: String) : RealtimeUiState
}

enum class PostureStatus { Unknown, Good, NeedsCorrection }

@HiltViewModel
class RealtimeExerciseViewModel @Inject constructor(
    private val poseEstimator: PoseEstimator,
    private val estimatorSession: PoseEstimatorSession,
    private val movementComparator: MovementComparator,
    private val routineSessionTracker: RoutineSessionTracker,
    private val sessionCamera: SessionCameraRepository,
    private val referenceRepository: ReferenceRepository
) : ViewModel() {

    /**
     * Respaldo de la referencia en uso. Arranca en [ReferenceProvenance.EMPAQUETADA]
     * ---la hipótesis conservadora--- y se ajusta al resolverse la referencia, de modo
     * que un retraso en esa consulta nunca hace que el sistema afirme de más.
     */
    private var provenance = ReferenceProvenance.EMPAQUETADA

    private val _uiState = MutableStateFlow<RealtimeUiState>(RealtimeUiState.CameraPermissionRequired)
    val uiState: StateFlow<RealtimeUiState> = _uiState.asStateFlow()

    /**
     * Última pose detectada, en un flujo aparte del resto del estado.
     *
     * Está separada de [RealtimeUiState.Active] a propósito: se actualiza a la cadencia de
     * la inferencia (~10-20 fps) y meterla en el estado compartido recomponía en cada frame
     * el badge, el contador de repeticiones y el botón. Acá sólo la consume el overlay.
     */
    private val _pose = MutableStateFlow<PoseFrame?>(null)
    val pose: StateFlow<PoseFrame?> = _pose.asStateFlow()

    val estadoModelo: StateFlow<EstadoEstimador> get() = estimatorSession.estado

    /** Instante de la última detección válida, para decidir cuándo la pose quedó obsoleta. */
    @Volatile
    private var lastPoseAtMs = 0L

    /**
     * Stream caliente de poses que alimenta al comparador. Capacidad 1 con DROP_OLDEST: si
     * la comparación se atrasa, se descartan poses viejas (backpressure end-to-end).
     */
    private val poseStream = MutableSharedFlow<PoseFrame>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var comparisonJob: Job? = null

    private var currentExerciseId = ""

    /** Nombre del ejercicio: elige el verbo de la corrección (ver [CorrectionMessages]). */
    private var currentExerciseName = ""

    /** Cronómetro de la serie en curso + acumulados del ejercicio (sin contar descansos). */
    private var seriesStartMs = 0L
    private var activeMillis = 0L
    private var totalReps = 0

    /** Llamar cuando el permiso de cámara está concedido. Idempotente. */
    fun initialize(exerciseId: String, exerciseName: String) {
        if (_uiState.value is RealtimeUiState.Active) return
        currentExerciseId = exerciseId
        currentExerciseName = exerciseName
        activeMillis = 0L
        totalReps = 0
        // Series y reps salen de la prescripción de la rutina; ejercicio suelto = 1 serie.
        val prescription = routineSessionTracker
            .takeIf { it.isCurrent(exerciseId) }
            ?.currentExercise()
        _uiState.value = RealtimeUiState.Active(
            exerciseName = exerciseName,
            targetReps = prescription?.repeticionesObjetivo ?: 10,
            totalSeries = prescription?.series ?: 1,
            feedbackMessage = READY_MESSAGE
        )
        // Arranca a contar de una: el chequeo de encuadre ya se hizo en "Prepará tu
        // espacio", así que pedir un "Comenzar" más acá era una pantalla pre-sesión
        // redundante. El descanso entre series siguientes sigue pidiendo el toque.
        onStartSeries()
        viewModelScope.launch {
            provenance = runCatching { referenceRepository.provenanceOf(exerciseId) }
                .getOrDefault(ReferenceProvenance.EMPAQUETADA)
        }
        startComparison(exerciseId)
        estimatorSession.start(viewModelScope)
    }

    fun onPermissionDenied() {
        _uiState.value = RealtimeUiState.Error("Se requiere permiso de cámara para continuar.")
    }

    /**
     * Procesa un frame de CameraX. Se invoca desde el hilo del `Analyzer` (fuera del main
     * thread); la inferencia es síncrona y el [image] se cierra siempre al terminar, lo que
     * libera el siguiente frame (con STRATEGY_KEEP_ONLY_LATEST esto da el descarte de
     * frames bajo carga).
     *
     * Cuando no hay detección **no se borra la pose de inmediato**: perder un frame suelto
     * es normal y limpiar en el acto haría parpadear el esqueleto. Recién se limpia tras
     * [STALE_POSE_MS] sin detecciones, que a esta cadencia son ~10 frames seguidos.
     */
    fun onFrame(image: ImageProxy) {
        try {
            // Ejercicio terminado: no se gasta inferencia en frames que nadie consume.
            if (_uiState.value !is RealtimeUiState.Active) return
            val pose = poseEstimator.estimate(image, image.imageInfo.rotationDegrees)
            val now = System.currentTimeMillis()
            if (pose != null) {
                lastPoseAtMs = now
                _pose.value = pose
                poseStream.tryEmit(pose)
            } else if (_pose.value != null && now - lastPoseAtMs > STALE_POSE_MS) {
                _pose.value = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando frame", e)
        } finally {
            image.close()
        }
    }

    fun onStartSeries() {
        val current = _uiState.value
        if (current is RealtimeUiState.Active && !current.seriesInProgress) {
            seriesStartMs = SystemClock.elapsedRealtime()
            _uiState.value = current.copy(
                seriesInProgress = true,
                postureStatus = PostureStatus.Unknown,
                feedbackMessage = "",
                goodStreak = 0,
                seriesAnnouncement = null
            )
        }
    }

    fun onStop() {
        val current = _uiState.value
        if (current !is RealtimeUiState.Active || !current.seriesInProgress) return
        activeMillis += SystemClock.elapsedRealtime() - seriesStartMs
        totalReps += current.repCount
        if (current.currentSeries < current.totalSeries) {
            // Descanso: contador en cero y conteo pausado hasta la próxima serie.
            _uiState.value = current.copy(
                currentSeries = current.currentSeries + 1,
                repCount = 0,
                seriesInProgress = false,
                postureStatus = PostureStatus.Unknown,
                feedbackMessage = READY_MESSAGE,
                goodStreak = 0,
                seriesAnnouncement = null
            )
        } else {
            _pose.value = null
            comparisonJob?.cancel()
            comparisonJob = null
            // Soltar el modelo apenas termina el ejercicio, no recién al salir.
            viewModelScope.launch(Dispatchers.Default) { estimatorSession.stop() }
            // Se registran las reps contadas, aunque sean 0: con series salteadas, completar
            // hasta la prescripción reportaría como hecho lo que el paciente no hizo.
            val reps = totalReps
            val durationSeconds = (activeMillis / 1000L).toInt()
            routineSessionTracker.recordExerciseResult(currentExerciseId, reps, durationSeconds)
            _uiState.value = RealtimeUiState.Completed(
                totalReps = reps,
                durationSeconds = durationSeconds
            )
        }
    }

    /**
     * Cierra la serie en curso sin llegar al objetivo, por el mismo camino que el cierre
     * automático: descanso o, en la última serie, ejercicio completado con las reps hechas.
     * Con el objetivo alcanzado no hace nada: el cierre automático ya está agendado, y si
     * se cerrara acá también, ese cierre pendiente terminaría la serie siguiente si el
     * paciente la arranca antes de que dispare.
     */
    fun onSkipSeries() {
        val current = _uiState.value
        if (current !is RealtimeUiState.Active) return
        if (!current.seriesInProgress) {
            // En el descanso, saltear es no hacer la serie que sigue: se abre y se cierra en
            // el acto, con 0 repeticiones, para que el registro de series quede completo.
            onStartSeries()
            onStop()
            return
        }
        if (current.repCount >= current.targetReps) return
        onStop()
    }

    /**
     * Consume el stream de poses a través del comparador DTW. Se arranca recién en
     * [initialize] porque el comparador necesita el exerciseId para cargar su referencia.
     */
    private fun startComparison(exerciseId: String) {
        if (comparisonJob != null) return
        comparisonJob = viewModelScope.launch {
            movementComparator.compare(exerciseId, poseStream).collect { result ->
                var seriesJustCompleted = false
                _uiState.update { current ->
                    // En descanso no se cuenta ni se corrige: el usuario se está preparando.
                    if (current is RealtimeUiState.Active && current.seriesInProgress) {
                        val newCount = current.repCount + result.repDelta
                        // Flanco de llegada al objetivo: anuncia y agenda el cierre automático.
                        seriesJustCompleted = result.repDelta > 0 &&
                            newCount >= current.targetReps &&
                            current.repCount < current.targetReps
                        current.copy(
                            repCount = newCount,
                            goodStreak = when {
                                result.repDelta == 0 -> current.goodStreak
                                result.status == MovementStatus.Good -> current.goodStreak + 1
                                else -> 0
                            },
                            postureStatus = result.status.toPostureStatus(),
                            feedbackMessage = result.toFeedbackMessage(current.feedbackMessage),
                            seriesAnnouncement = if (seriesJustCompleted) {
                                "${current.targetReps} de ${current.targetReps} repeticiones hechas, serie finalizada"
                            } else {
                                current.seriesAnnouncement
                            }
                        )
                    } else {
                        current
                    }
                }
                if (seriesJustCompleted) {
                    viewModelScope.launch {
                        // El anuncio necesita unos segundos en pantalla/TTS antes del cierre.
                        delay(AUTO_STOP_DELAY_MS)
                        onStop()
                    }
                }
            }
        }
    }

    /**
     * El lenguaje de la devolución se ajusta al respaldo de la referencia.
     *
     * Con una referencia registrada por el profesional, el sistema puede hablar de una
     * repetición *bien hecha*. Sin ese respaldo no puede: la referencia es la forma que la
     * propia persona grabó ---o una genérica de fábrica--- y afirmar corrección sería
     * confirmar como correcto lo que quizá no lo sea. En ese caso los mensajes se limitan a
     * la consistencia con la referencia disponible, que es lo que el sistema efectivamente
     * mide. Es la exigencia de RNF-17 llevada a este caso.
     */
    private fun ComparisonResult.toFeedbackMessage(previous: String): String {
        val percent = similarityScore?.let { (it * 100).roundToInt() }
        val respalda = provenance.respaldaCorreccion
        return when (status) {
            // Sin score (primera rep, segmento parcial): correcta por definición.
            MovementStatus.Good -> when {
                respalda && percent != null -> "¡Buena repetición! Similitud $percent%"
                respalda -> "¡Buena repetición!"
                percent != null -> "Coincide con tu referencia — $percent%"
                else -> "Coincide con tu referencia"
            }
            MovementStatus.NeedsCorrection ->
                // Con atribución, el mensaje nombra la parte del cuerpo, el sentido y la fase,
                // igual para cualquier referencia: es una instrucción, no un juicio de
                // corrección, así que no necesita el respaldo del profesional.
                hints.firstOrNull()
                    ?.let { CorrectionMessages.messageFor(it, currentExerciseName) }
                    ?: CorrectionMessages.sinAtribucion()
            MovementStatus.Unknown -> previous
        }
    }

    override fun onCleared() {
        super.onCleared()
        // La elección puntual de lente vale hasta acá: el próximo flujo vuelve a
        // arrancar desde la preferencia del rol.
        sessionCamera.limpiar()

        estimatorSession.stop()
    }

    private fun MovementStatus.toPostureStatus(): PostureStatus = when (this) {
        MovementStatus.Unknown -> PostureStatus.Unknown
        MovementStatus.Good -> PostureStatus.Good
        MovementStatus.NeedsCorrection -> PostureStatus.NeedsCorrection
    }

    companion object {
        private const val TAG = "RealtimeExerciseVM"

        /** Tiempo del anuncio de fin de serie en pantalla/TTS antes del cierre automático. */
        private const val AUTO_STOP_DELAY_MS = 2_500L

        private const val READY_MESSAGE = "Preparáte y tocá Comenzar cuando estés listo."

        /** Sin detecciones durante este lapso, el esqueleto se borra en vez de congelarse. */
        private const val STALE_POSE_MS = 500L
    }
}
