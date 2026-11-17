package com.steadypath.app.presentation.screens.exercises

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.model.ExerciseVideo
import com.steadypath.app.domain.usecase.GetExerciseVideosUseCase
import com.steadypath.app.domain.usecase.GetExercisesUseCase
import com.steadypath.app.domain.usecase.HasOwnReferenceUseCase
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.session.RoutineSessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExerciseDetailData(
    val exercise: Exercise,
    val formattedSteps: List<String>
)

/** Resultado de tocar «Comenzar»: la pantalla reacciona una vez y lo consume. */
sealed interface StartGate {
    data object Idle : StartGate
    data object Checking : StartGate
    data object Proceed : StartGate
    /** Sin referencia propia: hay que grabar una antes de que la app pueda comparar. */
    data object MissingReference : StartGate
}

@HiltViewModel
class ExerciseDetailViewModel @Inject constructor(
    private val getExercisesUseCase: GetExercisesUseCase,
    private val getExerciseVideosUseCase: GetExerciseVideosUseCase,
    private val hasOwnReferenceUseCase: HasOwnReferenceUseCase,
    private val routineSessionTracker: RoutineSessionTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ExerciseDetailData>>(UiState.Loading)
    val uiState: StateFlow<UiState<ExerciseDetailData>> = _uiState.asStateFlow()

    private val _startGate = MutableStateFlow<StartGate>(StartGate.Idle)
    val startGate: StateFlow<StartGate> = _startGate.asStateFlow()

    /**
     * Mantiene la secuencia si este ejercicio es el paso actual; si no, es un ejercicio suelto.
     * Antes de dejar pasar exige referencia propia: sin ella la comparación en vivo corre
     * contra la genérica de fábrica y la devolución no dice nada de esta persona. Un ejercicio
     * sin patrón de movimiento no puede tener referencia, así que pasa directo.
     */
    fun onStartExercise(exercise: Exercise) {
        if (_startGate.value == StartGate.Checking) return
        if (!routineSessionTracker.isCurrent(exercise.idEjercicio)) {
            routineSessionTracker.clear()
        }
        if (exercise.patronMovimiento == null) {
            _startGate.value = StartGate.Proceed
            return
        }
        _startGate.value = StartGate.Checking
        viewModelScope.launch {
            val hasReference = runCatching { hasOwnReferenceUseCase(exercise.idEjercicio) }
                .getOrDefault(false)
            _startGate.value = if (hasReference) StartGate.Proceed else StartGate.MissingReference
        }
    }

    fun consumeStartGate() {
        _startGate.value = StartGate.Idle
    }

    // Flow aparte: si el listado de videos falla, la descripción se muestra igual.
    private val _videosState = MutableStateFlow<UiState<List<ExerciseVideo>>>(UiState.Loading)
    val videosState: StateFlow<UiState<List<ExerciseVideo>>> = _videosState.asStateFlow()

    fun loadExercise(exerciseId: String) {
        loadVideos(exerciseId)
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            getExercisesUseCase().collect { result ->
                result.fold(
                    onSuccess = { exercises ->
                        val exercise = exercises.find { it.idEjercicio == exerciseId }
                        if (exercise != null) {
                            // Las descripciones se escriben como líneas separadas por salto
                            // de línea (una intro y viñetas "• "), no como oraciones unidas
                            // por ". " — ese delimitador no aparece nunca en el texto real.
                            val steps = exercise.descripcion
                                .split("\n")
                                .map { it.trim().removePrefix("•").trim() }
                                .filter { it.isNotBlank() }
                            _uiState.value = UiState.Success(ExerciseDetailData(exercise, steps))
                        } else {
                            _uiState.value = UiState.Error("Ejercicio no encontrado")
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = UiState.Error(
                            error.message ?: "Error al cargar ejercicio"
                        )
                    }
                )
            }
        }
    }

    private fun loadVideos(exerciseId: String) {
        viewModelScope.launch {
            _videosState.value = UiState.Loading
            getExerciseVideosUseCase(exerciseId).collect { result ->
                result.fold(
                    onSuccess = { videos -> _videosState.value = UiState.Success(videos) },
                    // Sin videos y error de listado se muestran igual: placeholder sin player.
                    onFailure = { _videosState.value = UiState.Success(emptyList()) }
                )
            }
        }
    }
}
