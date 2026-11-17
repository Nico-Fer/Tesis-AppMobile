package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.PatientSummary
import com.steadypath.app.domain.usecase.AssignTemplateUseCase
import com.steadypath.app.domain.usecase.GetMyPatientsUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChoosePatientForTemplateViewModel @Inject constructor(
    private val getMyPatientsUseCase: GetMyPatientsUseCase,
    private val assignTemplateUseCase: AssignTemplateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<PatientSummary>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<PatientSummary>>> = _uiState.asStateFlow()

    // Success(Unit) inicial = sin asignación en curso.
    private val _assignState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val assignState: StateFlow<UiState<Unit>> = _assignState.asStateFlow()

    private val _templateAssigned = MutableStateFlow(false)
    val templateAssigned: StateFlow<Boolean> = _templateAssigned.asStateFlow()

    fun loadPatients() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            getMyPatientsUseCase().collect { result ->
                result.fold(
                    onSuccess = { patients -> _uiState.value = UiState.Success(patients) },
                    onFailure = { error ->
                        _uiState.value = UiState.Error(error.message ?: "Error al cargar los pacientes")
                    }
                )
            }
        }
    }

    fun assignTemplate(idPlantilla: String, idPaciente: String) {
        viewModelScope.launch {
            _assignState.value = UiState.Loading
            assignTemplateUseCase(idPlantilla, idPaciente, emptyList()).collect { result ->
                result.fold(
                    onSuccess = {
                        _assignState.value = UiState.Success(Unit)
                        _templateAssigned.value = true
                    },
                    onFailure = { error ->
                        _assignState.value = UiState.Error(error.message ?: "Error al asignar la rutina")
                    }
                )
            }
        }
    }
}
