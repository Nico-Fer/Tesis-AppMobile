package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.model.RoutineTemplate
import com.steadypath.app.domain.repository.RoutineExerciseInput
import com.steadypath.app.domain.usecase.EditTemplateUseCase
import com.steadypath.app.domain.usecase.GetExercisesUseCase
import com.steadypath.app.domain.usecase.GetTemplateUseCase
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DraftExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplateDetailViewModel @Inject constructor(
    private val getTemplateUseCase: GetTemplateUseCase,
    private val editTemplateUseCase: EditTemplateUseCase,
    private val getExercisesUseCase: GetExercisesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<RoutineTemplate>>(UiState.Loading)
    val uiState: StateFlow<UiState<RoutineTemplate>> = _uiState.asStateFlow()

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    val nombre = MutableStateFlow("")
    val draftExercises = MutableStateFlow<List<DraftExercise>>(emptyList())

    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val saveState: StateFlow<UiState<Unit>> = _saveState.asStateFlow()

    private val _catalogState = MutableStateFlow<UiState<List<Exercise>>>(UiState.Loading)
    val catalogState: StateFlow<UiState<List<Exercise>>> = _catalogState.asStateFlow()

    fun loadTemplate(idPlantilla: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            getTemplateUseCase(idPlantilla).collect { result ->
                result.fold(
                    onSuccess = { template -> _uiState.value = UiState.Success(template) },
                    onFailure = { error ->
                        _uiState.value = UiState.Error(error.message ?: "Error al cargar la plantilla")
                    }
                )
            }
        }
    }

    fun startEditing() {
        val template = (_uiState.value as? UiState.Success)?.data ?: return
        nombre.value = template.nombreRutina
        draftExercises.value = template.ejercicios.map {
            DraftExercise(it.idEjercicio, it.nombreEjercicio, it.series, it.repeticionesObjetivo)
        }
        _isEditing.value = true
    }

    fun cancelEditing() {
        _isEditing.value = false
    }

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
                        _catalogState.value = UiState.Error(error.message ?: "Error al cargar el catálogo")
                    }
                )
            }
        }
    }

    fun addExercise(exercise: Exercise, series: Int, repeticiones: Int) {
        draftExercises.value = draftExercises.value
            .filterNot { it.idEjercicio == exercise.idEjercicio } +
            DraftExercise(exercise.idEjercicio, exercise.nombre, series, repeticiones)
    }

    fun updateExercise(idEjercicio: String, series: Int, repeticiones: Int) {
        draftExercises.value = draftExercises.value.map {
            if (it.idEjercicio == idEjercicio) it.copy(series = series, repeticiones = repeticiones) else it
        }
    }

    fun removeExercise(idEjercicio: String) {
        draftExercises.value = draftExercises.value.filterNot { it.idEjercicio == idEjercicio }
    }

    fun saveEdits(idPlantilla: String) {
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            val ejercicios = draftExercises.value.map {
                RoutineExerciseInput(it.idEjercicio, it.repeticiones, it.series)
            }
            editTemplateUseCase(idPlantilla, nombre.value.trim(), ejercicios).collect { result ->
                result.fold(
                    onSuccess = { template ->
                        _uiState.value = UiState.Success(template)
                        _saveState.value = UiState.Success(Unit)
                        _isEditing.value = false
                    },
                    onFailure = { error ->
                        _saveState.value = UiState.Error(error.message ?: "Error al guardar los cambios")
                    }
                )
            }
        }
    }
}
