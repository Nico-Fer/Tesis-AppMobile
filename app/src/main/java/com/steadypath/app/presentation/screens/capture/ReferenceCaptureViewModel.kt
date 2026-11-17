package com.steadypath.app.presentation.screens.capture

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import com.steadypath.app.domain.repository.SessionCameraRepository
import androidx.lifecycle.viewModelScope
import com.steadypath.app.BuildConfig
import com.steadypath.app.data.comparison.ReferenceJsonWriter
import com.steadypath.app.data.vision.EstadoEstimador
import com.steadypath.app.data.vision.PoseEstimator
import com.steadypath.app.data.vision.PoseEstimatorSession
import com.steadypath.app.domain.comparison.AngleExtractor
import com.steadypath.app.domain.comparison.AngleFrame
import com.steadypath.app.domain.comparison.ExerciseDefinition
import com.steadypath.app.domain.comparison.ExerciseReference
import com.steadypath.app.domain.comparison.FIRST_LAST_TOL_RAD
import com.steadypath.app.domain.comparison.MovementPattern
import com.steadypath.app.domain.comparison.MovementPatternCatalog
import com.steadypath.app.domain.comparison.RecordingSegmenterFactory
import com.steadypath.app.domain.comparison.RecordingSegmenterListener
import com.steadypath.app.domain.comparison.ReferenceMetadata
import com.steadypath.app.domain.comparison.ReferenceRepSelector
import com.steadypath.app.domain.comparison.RepRejectReason
import com.steadypath.app.domain.comparison.RepetitionSegmenter
import com.steadypath.app.domain.comparison.ScoringConfig
import com.steadypath.app.domain.comparison.cycleClosureErrorRad
import com.steadypath.app.domain.comparison.dtw.Dtw
import com.steadypath.app.domain.comparison.dtw.WrappedAngularDistance
import com.steadypath.app.domain.comparison.estimateFps
import com.steadypath.app.domain.model.PoseFrame
import com.steadypath.app.domain.repository.MovementReferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Resumen de una repetición capturada, para la lista de la UI.
 * Los frames crudos quedan en el ViewModel; acá solo lo mostrable.
 */
data class CapturedRepSummary(
    val index: Int,
    val frameCount: Int,
    val durationMs: Long
)

data class ReferenceCaptureUiState(
    val patterns: List<MovementPattern> = MovementPatternCatalog.patterns,
    val selectedPatternSlug: String = MovementPatternCatalog.patterns.first().slug,
    val exerciseId: String = "",
    val isRecording: Boolean = false,
    val reps: List<CapturedRepSummary> = emptyList(),
    val statusMessage: String = "",
    // Modo producción: ejercicio y patrón fijados por navegación (sin dropdown ni UUID manual).
    val exerciseName: String? = null,
    val targetPatientId: String? = null,
    val isUploading: Boolean = false,
    val uploadSuccess: Boolean = false,
    /** Hubo un intento de guardado que falló; las reps siguen en memoria para reintentar. */
    val uploadFailed: Boolean = false
) {
    /** Configurada por navegación (ejercicio fijo) en lugar de la herramienta debug. */
    val isProductionMode: Boolean get() = exerciseName != null
}

/**
 * Captura de referencia: quien graba ejecuta N reps frente a la cámara y se elige la rep
 * canónica (medoide DTW). También respalda la herramienta DEBUG de exportación a JSON v1.
 *
 * Reutiliza la misma instancia inyectada de [AngleExtractor] que el comparador en vivo. La
 * segmentación en cambio es propia ([RecordingSegmenterFactory]): sin referencia no hay
 * amplitud con la cual escalar umbrales, así que se derivan del reposo del paciente. El
 * medoide y el DTW siguen siendo los mismos del flujo en vivo.
 *
 * [announcements] lleva lo que la pantalla dice en voz alta: quien graba suele estar lejos
 * del teléfono y no lee el contador ni por qué una repetición no se contó.
 */
@HiltViewModel
class ReferenceCaptureViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val poseEstimator: PoseEstimator,
    private val estimatorSession: PoseEstimatorSession,
    private val angleExtractor: AngleExtractor,
    private val segmenterFactory: RecordingSegmenterFactory,
    private val movementReferenceRepository: MovementReferenceRepository,
    private val sessionCamera: SessionCameraRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReferenceCaptureUiState())
    val uiState: StateFlow<ReferenceCaptureUiState> = _uiState.asStateFlow()

    /** Igual que en RealtimeExerciseViewModel: la pose va aparte para no recomponer todo a 20 fps. */
    private val _pose = MutableStateFlow<PoseFrame?>(null)
    val pose: StateFlow<PoseFrame?> = _pose.asStateFlow()

    val estadoModelo: StateFlow<EstadoEstimador> get() = estimatorSession.estado

    /** Archivo exportado listo para compartir (la Screen dispara el share intent). */
    private val _exportedFile = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val exportedFile: SharedFlow<File> = _exportedFile.asSharedFlow()

    /** Frases para decir en voz alta; se emiten también desde el hilo del Analyzer. */
    private val _announcements = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val announcements: SharedFlow<String> = _announcements.asSharedFlow()

    private val capturedReps = mutableListOf<List<AngleFrame>>()

    // Solo se tocan desde el hilo del Analyzer mientras isRecording.
    private var segmenter: RepetitionSegmenter? = null
    private var recordingDefinition: ExerciseDefinition? = null

    /** El segmentador descartó acomodos en este cuadro: la rep que sigue reinicia la cuenta. */
    private var countRestarted = false

    @Volatile
    private var lastPoseAtMs = 0L

    fun initialize() {
        estimatorSession.start(viewModelScope)
    }

    /**
     * Modo producción: fija ejercicio, patrón y destinatario desde la navegación.
     * [patientId] no nulo = graba el profesional para ese paciente; nulo = referencia propia.
     */
    fun configure(exerciseId: String, exerciseName: String, patternSlug: String, patientId: String?) {
        if (_uiState.value.exerciseName != null) return // ya configurado (recomposición)
        val pattern = MovementPatternCatalog.bySlug(patternSlug)
        if (pattern == null) {
            _uiState.update {
                it.copy(
                    exerciseName = exerciseName,
                    statusMessage = "El ejercicio declara un patrón desconocido ('$patternSlug'). " +
                        "Actualizá la app para grabar esta referencia."
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedPatternSlug = pattern.slug,
                exerciseId = exerciseId,
                exerciseName = exerciseName,
                targetPatientId = patientId
            )
        }
    }

    fun onPatternSelected(slug: String) {
        if (_uiState.value.isRecording) return
        _uiState.update { it.copy(selectedPatternSlug = slug) }
        clearReps()
    }

    fun onExerciseIdChanged(value: String) {
        _uiState.update { it.copy(exerciseId = value) }
    }

    fun startRecording() {
        val pattern = MovementPatternCatalog.bySlug(_uiState.value.selectedPatternSlug) ?: return
        // En producción cada toma arranca limpia: las reps de un intento fallido no se mezclan.
        if (_uiState.value.isProductionMode) capturedReps.clear()
        // El exerciseId real se vincula recién al exportar; para extraer alcanza el slug.
        recordingDefinition = pattern.toExerciseDefinition(pattern.slug)
        segmenter = segmenterFactory.create(pattern.driverAngleIndex, segmenterListener)
        countRestarted = false
        _uiState.update {
            it.copy(
                isRecording = true,
                reps = if (it.isProductionMode) emptyList() else it.reps,
                uploadFailed = false,
                statusMessage = "Quedate quieto en la posición inicial…"
            )
        }
        announce("Quedate quieto en la posición inicial.")
    }

    // Llega en el hilo del Analyzer, igual que processForCapture: solo toca _uiState.
    private val segmenterListener = object : RecordingSegmenterListener {
        override fun onCalibrated() {
            if (!_uiState.value.isRecording) return
            _uiState.update { it.copy(statusMessage = RECORDING_MESSAGE) }
            announce("Ya podés empezar.")
        }

        override fun onRejected(reason: RepRejectReason) {
            if (!_uiState.value.isRecording) return
            val message = when (reason) {
                RepRejectReason.TOO_SHORT -> "Muy rápido, no se contó. Hacelo más despacio."
                RepRejectReason.BAD_SHAPE -> "No se contó: hacé el movimiento de una vez y volvé al reposo."
                RepRejectReason.WRONG_DIRECTION -> "Esa repetición fue en otra dirección, no se contó."
            }
            _uiState.update { it.copy(statusMessage = message) }
            announce(message)
        }

        // Llega dentro de segmenter.onFrame, antes de que devuelva la rep que los reemplaza.
        override fun onRetracted(count: Int) {
            val retracted = count.coerceAtMost(capturedReps.size)
            repeat(retracted) { capturedReps.removeAt(capturedReps.lastIndex) }
            _uiState.update { it.copy(reps = it.reps.dropLast(retracted)) }
            countRestarted = true
        }

        override fun onDiagnostic(message: String) {
            if (BuildConfig.DEBUG) Log.d(SEGMENTER_TAG, message)
        }
    }

    private fun announce(message: String) {
        _announcements.tryEmit(message)
    }

    fun stopRecording() {
        segmenter?.reset()
        segmenter = null
        recordingDefinition = null
        _uiState.update { it.copy(isRecording = false, statusMessage = "") }
    }

    /**
     * Modo producción: corta la toma y, si alcanzan las reps, elige la mejor y la sube sin
     * más intervención. Con menos de [MIN_REPS_FOR_UPLOAD] se descarta la toma y se explica.
     */
    fun finishRecording() {
        stopRecording()
        val count = capturedReps.size
        if (count >= MIN_REPS_FOR_UPLOAD) {
            saveToBackend()
        } else {
            capturedReps.clear()
            _uiState.update {
                it.copy(
                    reps = emptyList(),
                    statusMessage = "Se detectaron $count repeticiones; hacen falta al menos " +
                        "$MIN_REPS_FOR_UPLOAD. Tocá Comenzar para intentar de nuevo."
                )
            }
            announce("Se contaron $count repeticiones; hacen falta al menos $MIN_REPS_FOR_UPLOAD.")
        }
    }

    fun clearReps() {
        capturedReps.clear()
        _uiState.update { it.copy(reps = emptyList(), statusMessage = "", uploadFailed = false) }
    }

    /** Invocado en el hilo del Analyzer; misma disciplina que el flujo en vivo. */
    fun onFrame(image: ImageProxy) {
        try {
            val pose = poseEstimator.estimate(image, image.imageInfo.rotationDegrees)
            val now = System.currentTimeMillis()
            if (pose != null) {
                lastPoseAtMs = now
                _pose.value = pose
                processForCapture(pose)
            } else if (_pose.value != null && now - lastPoseAtMs > STALE_POSE_MS) {
                _pose.value = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error procesando frame", e)
        } finally {
            image.close()
        }
    }

    private fun processForCapture(pose: PoseFrame) {
        val definition = recordingDefinition ?: return
        val currentSegmenter = segmenter ?: return
        val angleFrame = angleExtractor.extract(pose, definition)
        // Más estricto que el comparador en vivo (que tolera dims parciales): una
        // referencia solo puede construirse con frames 100% confiables (el esquema v1
        // no tiene máscara de validez). En captura supervisada esto es alcanzable.
        if (!angleFrame.valid.all { it }) return
        val completedRep = currentSegmenter.onFrame(angleFrame) ?: return
        val restarted = countRestarted
        countRestarted = false
        capturedReps.add(completedRep)
        val summary = CapturedRepSummary(
            index = capturedReps.size,
            frameCount = completedRep.size,
            durationMs = completedRep.last().timestampMs - completedRep.first().timestampMs
        )
        _uiState.update {
            it.copy(
                reps = it.reps + summary,
                statusMessage = if (restarted) RESTARTED_MESSAGE else RECORDING_MESSAGE
            )
        }
        // Mismo cierre automático que la serie en vivo: al llegar al objetivo no hay botón
        // que tocar, la app elige la mejor rep y la guarda.
        if (_uiState.value.isProductionMode && capturedReps.size >= TARGET_REPS) {
            announce("${capturedReps.size}. ¡Listo!")
            finishRecording()
        } else if (restarted) {
            // Una sola frase: dos anuncios seguidos se pisan.
            announce("Empiezo a contar de nuevo: ${capturedReps.size}.")
        } else {
            announce("${capturedReps.size}")
        }
    }

    /** Elige la rep canónica (medoide DTW, sin outliers) y exporta el JSON v1. */
    fun export() {
        val state = _uiState.value
        val exerciseId = state.exerciseId.trim()
        if (exerciseId.isBlank()) {
            _uiState.update { it.copy(statusMessage = "Ingresá el UUID del ejercicio antes de exportar") }
            return
        }
        val pattern = MovementPatternCatalog.bySlug(state.selectedPatternSlug) ?: return
        val reps = capturedReps.toList()
        if (reps.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "No hay repeticiones capturadas") }
            return
        }

        viewModelScope.launch(Dispatchers.Default) {
            runCatching {
                val (reference, selection) = buildCanonicalReference(pattern, exerciseId, reps)
                val jsonText = ReferenceJsonWriter.write(reference)

                val dir = File(context.getExternalFilesDir(null), "references").apply { mkdirs() }
                val file = File(dir, "$exerciseId.json")
                file.writeText(jsonText)
                file to selection
            }.onSuccess { (file, selection) ->
                val discarded = if (selection.discardedIndices.isEmpty()) ""
                else " (descartadas: ${selection.discardedIndices.map { it + 1 }})"
                _uiState.update {
                    it.copy(
                        statusMessage = "Exportada rep ${selection.selectedIndex + 1} de " +
                            "${reps.size}$discarded → ${file.name}"
                    )
                }
                _exportedFile.tryEmit(file)
            }.onFailure { e ->
                Log.e(TAG, "Fallo al exportar la referencia", e)
                _uiState.update { it.copy(statusMessage = "Error al exportar: ${e.message}") }
            }
        }
    }

    /**
     * Modo producción: elige la rep canónica, valida el cierre de ciclo y sube la referencia
     * al backend (propia o del paciente según [ReferenceCaptureUiState.targetPatientId]).
     */
    fun saveToBackend() {
        val state = _uiState.value
        if (state.isUploading) return
        val exerciseId = state.exerciseId.trim()
        val pattern = MovementPatternCatalog.bySlug(state.selectedPatternSlug)
        if (exerciseId.isBlank() || pattern == null) return
        val reps = capturedReps.toList()
        if (reps.size < MIN_REPS_FOR_UPLOAD) {
            _uiState.update {
                it.copy(
                    statusMessage = "Se necesitan al menos $MIN_REPS_FOR_UPLOAD repeticiones " +
                        "para elegir la mejor (hay ${reps.size})."
                )
            }
            return
        }

        _uiState.update {
            it.copy(isUploading = true, uploadFailed = false, statusMessage = "Eligiendo la mejor repetición…")
        }
        viewModelScope.launch(Dispatchers.Default) {
            val built = runCatching { buildCanonicalReference(pattern, exerciseId, reps) }
                .getOrElse { e ->
                    Log.e(TAG, "Fallo al elegir la rep canónica", e)
                    _uiState.update {
                        it.copy(isUploading = false, uploadFailed = true, statusMessage = "Error: ${e.message}")
                    }
                    announce(SAVE_FAILED_ANNOUNCEMENT)
                    return@launch
                }
            val (reference, selection) = built

            // Paridad con schema.py de modelosCV: el ciclo debe cerrar (reposo→extremo→reposo).
            val closure = cycleClosureErrorRad(reference.trajectory)
            if (closure > FIRST_LAST_TOL_RAD) {
                // Reintentar la subida no cambia nada: la toma es la que no sirve.
                capturedReps.clear()
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        reps = emptyList(),
                        statusMessage = "La repetición elegida no vuelve a la posición inicial " +
                            "(desvío %.2f rad). Volvé a grabar terminando en reposo.".format(closure)
                    )
                }
                announce("La repetición no volvió a la posición inicial. Volvé a grabar terminando en reposo.")
                return@launch
            }

            val jsonText = ReferenceJsonWriter.write(reference)
            val patientId = state.targetPatientId
            val result = if (patientId == null) {
                movementReferenceRepository.uploadOwn(exerciseId, jsonText)
            } else {
                movementReferenceRepository.uploadForPatient(patientId, exerciseId, jsonText)
            }

            result.fold(
                onSuccess = {
                    val discarded = if (selection.discardedIndices.isEmpty()) ""
                    else " (descartadas: ${selection.discardedIndices.map { it + 1 }})"
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadSuccess = true,
                            statusMessage = "Referencia guardada: rep ${selection.selectedIndex + 1} " +
                                "de ${reps.size}$discarded"
                        )
                    }
                    announce("Referencia guardada.")
                },
                onFailure = { e ->
                    Log.e(TAG, "Fallo al subir la referencia", e)
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            uploadFailed = true,
                            statusMessage = "No se pudo guardar: ${e.message}"
                        )
                    }
                    announce(SAVE_FAILED_ANNOUNCEMENT)
                }
            )
        }
    }

    /** Selección de medoide + armado del JSON v1 — compartido por export() y saveToBackend(). */
    private fun buildCanonicalReference(
        pattern: MovementPattern,
        exerciseId: String,
        reps: List<List<AngleFrame>>
    ): Pair<ExerciseReference, ReferenceRepSelector.Selection> {
        val definition = pattern.toExerciseDefinition(exerciseId)
        val weights = FloatArray(definition.dimensions) { definition.angleDefinitions[it].weight }
        val selector = ReferenceRepSelector(Dtw(WrappedAngularDistance(weights)))
        val selection = selector.select(reps)
        val canonical = reps[selection.selectedIndex]

        val reference = ExerciseReference(
            definition = definition,
            trajectory = canonical,
            metadata = ReferenceMetadata(
                fps = estimateFps(canonical),
                version = "1.0",
                source = "captura-in-app:${pattern.slug}"
            ),
            // TODO: calibrar con datos.
            scoring = ScoringConfig(similarityScale = 0.8, goodThreshold = 0.6f)
        )
        return reference to selection
    }

    override fun onCleared() {
        super.onCleared()
        // La elección puntual de lente vale hasta acá: el próximo flujo vuelve a
        // arrancar desde la preferencia del rol.
        sessionCamera.limpiar()

        estimatorSession.stop()
    }

    companion object {
        private const val TAG = "ReferenceCaptureVM"

        /** Decisiones del segmentador de grabación (solo DEBUG): `adb logcat -s RecSeg`. */
        private const val SEGMENTER_TAG = "RecSeg"
        private const val STALE_POSE_MS = 500L
        private const val RECORDING_MESSAGE = "Grabando — ejecutá las repeticiones"
        private const val RESTARTED_MESSAGE =
            "Los movimientos anteriores eran mucho más chicos que este: empiezo a contar de nuevo."
        private const val SAVE_FAILED_ANNOUNCEMENT = "No se pudo guardar la referencia."

        /** Con menos de 3 reps no hay base estadística para medoide+outliers (modelosCV usa 3–7). */
        const val MIN_REPS_FOR_UPLOAD = 3

        /** Reps a las que la toma se cierra sola en producción (dentro del rango 3–7 de modelosCV). */
        const val TARGET_REPS = 5
    }
}
