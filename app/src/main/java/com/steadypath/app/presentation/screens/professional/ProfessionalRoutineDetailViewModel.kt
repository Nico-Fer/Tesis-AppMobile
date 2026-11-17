package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.RoutineCompliance
import com.steadypath.app.domain.usecase.GetRoutineComplianceUseCase
import com.steadypath.app.domain.usecase.GetRoutineUseCase
import com.steadypath.app.domain.usecase.SetRoutineActiveUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfessionalRoutineDetailData(
    val routine: Routine,
    val compliance: RoutineCompliance? = null
)

@HiltViewModel
class ProfessionalRoutineDetailViewModel @Inject constructor(
    private val getRoutineUseCase: GetRoutineUseCase,
    private val getRoutineComplianceUseCase: GetRoutineComplianceUseCase,
    private val setRoutineActiveUseCase: SetRoutineActiveUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ProfessionalRoutineDetailData>>(UiState.Loading)
    val uiState: StateFlow<UiState<ProfessionalRoutineDetailData>> = _uiState.asStateFlow()

    fun loadRoutine(routineId: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            var routine: Routine? = null
            getRoutineUseCase(routineId).collect { result ->
                result.fold(
                    onSuccess = { routine = it },
                    onFailure = { error ->
                        _uiState.value = UiState.Error(error.message ?: "Error al cargar la rutina")
                    }
                )
            }

            val loadedRoutine = routine ?: return@launch

            var compliance: RoutineCompliance? = null
            getRoutineComplianceUseCase(routineId).collect { result ->
                result.fold(
                    onSuccess = { compliance = it },
                    onFailure = { /* keep defaults */ }
                )
            }

            _uiState.value = UiState.Success(
                ProfessionalRoutineDetailData(routine = loadedRoutine, compliance = compliance)
            )
        }
    }

    fun setActive(routineId: String, activa: Boolean) {
        viewModelScope.launch {
            setRoutineActiveUseCase(routineId, activa).collect { result ->
                result.fold(
                    onSuccess = { loadRoutine(routineId) },
                    onFailure = { /* deja el switch como estaba; el usuario puede reintentar */ }
                )
            }
        }
    }
}
