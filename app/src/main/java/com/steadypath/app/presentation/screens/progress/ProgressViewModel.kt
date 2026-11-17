package com.steadypath.app.presentation.screens.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.ProgressReport
import com.steadypath.app.domain.repository.UserSessionRepository
import com.steadypath.app.domain.usecase.GetProgressReportUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val getProgressReportUseCase: GetProgressReportUseCase,
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ProgressReport>>(UiState.Loading)
    val uiState: StateFlow<UiState<ProgressReport>> = _uiState.asStateFlow()

    private val _patientName = MutableStateFlow("")
    val patientName: StateFlow<String> = _patientName.asStateFlow()

    // El nombre se relee en cada entrada (no en init) por si se editó el perfil mientras
    // tanto — mismo fix que en DashboardViewModel y RoutineListViewModel.
    fun loadProgress() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val usuario = userSessionRepository.get()
            _patientName.value = usuario?.nombre.orEmpty()
            val patientId = usuario?.idUsuario
            if (patientId == null) {
                _uiState.value = UiState.Error("No se pudo identificar al paciente")
                return@launch
            }
            getProgressReportUseCase(patientId).collect { result ->
                result.fold(
                    onSuccess = { _uiState.value = UiState.Success(it) },
                    onFailure = {
                        _uiState.value = UiState.Error(it.message ?: "Error al cargar el progreso")
                    }
                )
            }
        }
    }
}
