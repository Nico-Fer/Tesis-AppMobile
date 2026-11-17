package com.steadypath.app.presentation.screens.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.repository.RoutineExerciseInput
import com.steadypath.app.domain.usecase.CreateOwnRoutineUseCase
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
class CreateRoutineViewModel @Inject constructor(
    private val createOwnRoutineUseCase: CreateOwnRoutineUseCase,
    private val getExercisesUseCase: GetExercisesUseCase
) : ViewModel() {

    val nombre = MutableStateFlow("")
    val draftExercises = MutableStateFlow<List<DraftExercise>>(emptyList())

    // Opcional: vacío = rutina flexible sin días fijos.
    private val _diasSeleccionados = MutableStateFlow<Set<DiaSemana>>(emptySet())
    val diasSeleccionados: StateFlow<Set<DiaSemana>> = _diasSeleccionados.asStateFlow()

    // Success(Unit) inicial = sin guardado en curso (mismo patrón que LoginViewModel).
    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val saveState: StateFlow<UiState<Unit>> = _saveState.asStateFlow()

    // true cuando el backend confirmó la creación: dispara el modal "¡Rutina creada!".
    private val _routineCreated = MutableStateFlow(false)
    val routineCreated: StateFlow<Boolean> = _routineCreated.asStateFlow()

    private val _catalogState = MutableStateFlow<UiState<List<Exercise>>>(UiState.Loading)
    val catalogState: StateFlow<UiState<List<Exercise>>> = _catalogState.asStateFlow()

    val isFormValid: StateFlow<Boolean> = combine(nombre, draftExercises) { n, ejercicios ->
        n.isNotBlank() && ejercicios.isNotEmpty()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onNombreChange(value: String) {
        nombre.value = value
    }

    fun toggleDia(dia: DiaSemana) {
        _diasSeleccionados.value = _diasSeleccionados.value.let {
            if (dia in it) it - dia else it + dia
        }
    }

    fun setTodosLosDias(seleccionar: Boolean) {
        _diasSeleccionados.value = if (seleccionar) DiaSemana.entries.toSet() else emptySet()
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
        // Re-agregar un ejercicio ya elegido reemplaza su configuración.
        draftExercises.value = draftExercises.value
            .filterNot { it.idEjercicio == exercise.idEjercicio } +
            DraftExercise(
                idEjercicio = exercise.idEjercicio,
                nombre = exercise.nombre,
                series = series,
                repeticiones = repeticiones,
                patronMovimiento = exercise.patronMovimiento
            )
    }

    fun removeExercise(idEjercicio: String) {
        draftExercises.value = draftExercises.value.filterNot { it.idEjercicio == idEjercicio }
    }

    fun saveRoutine() {
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            val ejercicios = draftExercises.value.map {
                RoutineExerciseInput(
                    idEjercicio = it.idEjercicio,
                    repeticionesObjetivo = it.repeticiones,
                    series = it.series
                )
            }
            createOwnRoutineUseCase(
                nombre.value.trim(),
                _diasSeleccionados.value.sortedBy { it.ordinal },
                ejercicios
            ).collect { result ->
                result.fold(
                    onSuccess = {
                        _saveState.value = UiState.Success(Unit)
                        _routineCreated.value = true
                    },
                    onFailure = { error ->
                        _saveState.value =
                            UiState.Error(error.message ?: "Error al guardar la rutina")
                    }
                )
            }
        }
    }
}
