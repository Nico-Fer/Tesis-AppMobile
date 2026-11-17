package com.steadypath.app.presentation.screens.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.usecase.GetRoutineUseCase
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.session.RoutineSessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineDetailViewModel @Inject constructor(
    private val getRoutineUseCase: GetRoutineUseCase,
    private val routineSessionTracker: RoutineSessionTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Routine>>(UiState.Loading)
    val uiState: StateFlow<UiState<Routine>> = _uiState.asStateFlow()

    /** Arranca la sesión secuenciada; PainReport la usa para encadenar el próximo ejercicio. */
    fun startRoutineSession(fromIdEjercicio: String? = null) {
        (uiState.value as? UiState.Success)?.data?.let {
            routineSessionTracker.start(it, fromIdEjercicio)
        }
    }

    fun loadRoutine(routineId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            getRoutineUseCase(routineId).collect { result ->
                result.fold(
                    onSuccess = { routine -> _uiState.value = UiState.Success(routine) },
                    onFailure = { error ->
                        _uiState.value = UiState.Error(error.message ?: "Error al cargar la rutina")
                    }
                )
            }
        }
    }
}
