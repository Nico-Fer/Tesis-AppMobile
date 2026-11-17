package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.repository.RoutineExerciseInput
import com.steadypath.app.domain.usecase.CreateTemplateUseCase
import com.steadypath.app.domain.usecase.GetExercisesUseCase
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DraftExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateTemplateViewModel @Inject constructor(
    private val createTemplateUseCase: CreateTemplateUseCase,
    private val getExercisesUseCase: GetExercisesUseCase
) : ViewModel() {

    val nombre = MutableStateFlow("")
    val draftExercises = MutableStateFlow<List<DraftExercise>>(emptyList())

    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val saveState: StateFlow<UiState<Unit>> = _saveState.asStateFlow()

    private val _templateCreated = MutableStateFlow(false)
    val templateCreated: StateFlow<Boolean> = _templateCreated.asStateFlow()

    private val _catalogState = MutableStateFlow<UiState<List<Exercise>>>(UiState.Loading)
    val catalogState: StateFlow<UiState<List<Exercise>>> = _catalogState.asStateFlow()

    val isFormValid: StateFlow<Boolean> = combine(nombre, draftExercises) { n, ejercicios ->
        n.isNotBlank() && ejercicios.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onNombreChange(value: String) {
        nombre.value = value
    }

    fun loadCatalog() {
        viewModelScope.launch {
            _catalogState.value = UiState.Loading
            getExercisesUseCase().collect { result ->
                result.fold(
                    onSuccess = { exercises -> _catalogState.value = UiState.Success(exercises) },
                    onFailure = { error ->
                        _catalogState.value =
                            UiState.Error(error.message ?: "Error al cargar el catálogo")
                    }
                )
            }
        }
    }

    fun addExercise(exercise: Exercise, series: Int, repeticiones: Int) {
        draftExercises.value = draftExercises.value
            .filterNot { it.idEjercicio == exercise.idEjercicio } +
            DraftExercise(
                idEjercicio = exercise.idEjercicio,
                nombre = exercise.nombre,
                series = series,
                repeticiones = repeticiones
            )
    }

    fun removeExercise(idEjercicio: String) {
        draftExercises.value = draftExercises.value.filterNot { it.idEjercicio == idEjercicio }
    }

    fun saveTemplate() {
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            val ejercicios = draftExercises.value.map {
                RoutineExerciseInput(
                    idEjercicio = it.idEjercicio,
                    repeticionesObjetivo = it.repeticiones,
                    series = it.series
                )
            }
            createTemplateUseCase(nombre.value.trim(), ejercicios).collect { result ->
                result.fold(
                    onSuccess = {
                        _saveState.value = UiState.Success(Unit)
                        _templateCreated.value = true
                    },
                    onFailure = { error ->
                        _saveState.value = UiState.Error(error.message ?: "Error al crear la plantilla")
                    }
                )
            }
        }
    }
}
