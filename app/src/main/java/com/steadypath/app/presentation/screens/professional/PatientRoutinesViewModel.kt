package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.usecase.GetPatientRoutinesUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatientRoutinesViewModel @Inject constructor(
    private val getPatientRoutinesUseCase: GetPatientRoutinesUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<Routine>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<Routine>>> = _uiState.asStateFlow()

    fun loadRoutines(idPaciente: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            getPatientRoutinesUseCase(idPaciente, soloActivas = true).collect { result ->
                result.fold(
                    onSuccess = { rutinas -> _uiState.value = UiState.Success(rutinas) },
                    onFailure = { error ->
                        _uiState.value = UiState.Error(error.message ?: "Error al cargar las rutinas")
                    }
                )
            }
        }
    }
}
