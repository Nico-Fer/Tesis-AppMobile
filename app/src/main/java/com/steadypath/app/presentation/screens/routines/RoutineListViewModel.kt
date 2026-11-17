package com.steadypath.app.presentation.screens.routines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.repository.UserSessionRepository
import com.steadypath.app.domain.usecase.GetPatientRoutinesUseCase
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.session.RoutineSessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RoutineListViewModel @Inject constructor(
    private val getPatientRoutinesUseCase: GetPatientRoutinesUseCase,
    private val userSessionRepository: UserSessionRepository,
    private val routineSessionTracker: RoutineSessionTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Routine>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Routine>>> = _uiState.asStateFlow()

    private val _patientName = MutableStateFlow("")
    val patientName: StateFlow<String> = _patientName.asStateFlow()

    // Se llama desde la pantalla en cada entrada: al volver de crear una rutina la lista debe
    // refrescar, y el nombre se relee acá en vez de en init por si se editó el perfil mientras
    // tanto (ver el mismo fix en DashboardViewModel).
    fun loadRoutines() {
        // Estar en la lista implica que no hay secuencia en curso; descarta rutinas abandonadas.
        routineSessionTracker.clear()
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val usuario = userSessionRepository.get()
            _patientName.value = usuario?.nombre.orEmpty()
            val patientId = usuario?.idUsuario
            if (patientId == null) {
                _uiState.value = UiState.Error("No se pudo identificar al paciente")
                return@launch
            }
            getPatientRoutinesUseCase(patientId).collect { result ->
                result.fold(
                    onSuccess = { routines -> _uiState.value = UiState.Success(routines) },
                    onFailure = { error ->
                        _uiState.value = UiState.Error(error.message ?: "Error al cargar las rutinas")
                    }
                )
            }
        }
    }
}
