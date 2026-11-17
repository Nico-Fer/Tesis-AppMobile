package com.steadypath.app.presentation.screens.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.repository.RoutineExerciseInput
import com.steadypath.app.domain.usecase.EditRoutineExercisesUseCase
import com.steadypath.app.domain.usecase.GetExercisesUseCase
import com.steadypath.app.domain.usecase.GetRoutineUseCase
import com.steadypath.app.domain.usecase.SetRoutineActiveUseCase
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DraftExercise
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditAssignedRoutineViewModel @Inject constructor(
    private val getRoutineUseCase: GetRoutineUseCase,
    private val editRoutineExercisesUseCase: EditRoutineExercisesUseCase,
    private val setRoutineActiveUseCase: SetRoutineActiveUseCase,
    private val getExercisesUseCase: GetExercisesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Routine>>(UiState.Loading)
    val uiState: StateFlow<UiState<Routine>> = _uiState.asStateFlow()

    val diasSeleccionados = MutableStateFlow<Set<DiaSemana>>(emptySet())
    val draftExercises = MutableStateFlow<List<DraftExercise>>(emptyList())

    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val saveState: StateFlow<UiState<Unit>> = _saveState.asStateFlow()

    private val _routineDeactivated = MutableStateFlow(false)
    val routineDeactivated: StateFlow<Boolean> = _routineDeactivated.asStateFlow()

    private val _catalogState = MutableStateFlow<UiState<List<Exercise>>>(UiState.Loading)
    val catalogState: StateFlow<UiState<List<Exercise>>> = _catalogState.asStateFlow()

    fun loadRoutine(idRutina: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            getRoutineUseCase(idRutina).collect { result ->
                result.fold(
                    onSuccess = { routine ->
                        _uiState.value = UiState.Success(routine)
                        diasSeleccionados.value = routine.diasAsignados.toSet()
                        draftExercises.value = routine.ejercicios.map {
                            DraftExercise(it.idEjercicio, it.nombreEjercicio, it.series, it.repeticionesObjetivo)
                        }
                    },
                    onFailure = { error ->
                        _uiState.value = UiState.Error(error.message ?: "Error al cargar la rutina")
                    }
                )
            }
        }
    }

    fun toggleDia(dia: DiaSemana) {
        diasSeleccionados.value = diasSeleccionados.value.let {
            if (dia in it) it - dia else it + dia
        }
    }

    fun setTodosLosDias(seleccionar: Boolean) {
        diasSeleccionados.value = if (seleccionar) DiaSemana.entries.toSet() else emptySet()
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

    fun saveChanges(idRutina: String) {
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            val ejercicios = draftExercises.value.map {
                RoutineExerciseInput(it.idEjercicio, it.repeticiones, it.series)
            }
            editRoutineExercisesUseCase(
                idRutina,
                diasSeleccionados.value.sortedBy { it.ordinal },
                ejercicios
            ).collect { result ->
                result.fold(
                    onSuccess = { routine ->
                        _uiState.value = UiState.Success(routine)
                        _saveState.value = UiState.Success(Unit)
                    },
                    onFailure = { error ->
                        _saveState.value = UiState.Error(error.message ?: "Error al guardar los cambios")
                    }
                )
            }
        }
    }

    fun deactivateRoutine(idRutina: String) {
        viewModelScope.launch {
            setRoutineActiveUseCase(idRutina, false).collect { result ->
                result.fold(
                    onSuccess = { _routineDeactivated.value = true },
                    onFailure = { /* el usuario puede reintentar */ }
                )
            }
        }
    }
}
