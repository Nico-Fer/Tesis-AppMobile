package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.model.PatientSummary
import com.steadypath.app.domain.repository.UserSessionRepository
import com.steadypath.app.domain.usecase.CancelInvitationUseCase
import com.steadypath.app.domain.usecase.GetComplianceUseCase
import com.steadypath.app.domain.usecase.GetInvitationsUseCase
import com.steadypath.app.domain.usecase.GetMyPatientsUseCase
import com.steadypath.app.domain.usecase.InvitePatientUseCase
import com.steadypath.app.domain.usecase.RemovePatientUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PatientListData(
    val patients: List<PatientSummary> = emptyList(),
    val cumplimientoPorPaciente: Map<String, Int> = emptyMap(),
    val invitations: List<Invitation> = emptyList()
)

@HiltViewModel
class PatientListViewModel @Inject constructor(
    private val getMyPatientsUseCase: GetMyPatientsUseCase,
    private val getComplianceUseCase: GetComplianceUseCase,
    private val getInvitationsUseCase: GetInvitationsUseCase,
    private val invitePatientUseCase: InvitePatientUseCase,
    private val cancelInvitationUseCase: CancelInvitationUseCase,
    private val removePatientUseCase: RemovePatientUseCase,
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<PatientListData>>(UiState.Loading)
    val uiState: StateFlow<UiState<PatientListData>> = _uiState.asStateFlow()

    private val _nombreProfesional = MutableStateFlow("")
    val nombreProfesional: StateFlow<String> = _nombreProfesional.asStateFlow()

    // Success(null) inicial = sin invitación en curso; el mensaje de error se limpia solo.
    private val _inviteState = MutableStateFlow<UiState<Unit>?>(null)
    val inviteState: StateFlow<UiState<Unit>?> = _inviteState.asStateFlow()

    fun loadPatients() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _nombreProfesional.value = userSessionRepository.get()?.nombre.orEmpty()
            refreshData()
        }
    }

    private suspend fun refreshData() {
        var patients: List<PatientSummary> = emptyList()
        var errorMessage: String? = null
        getMyPatientsUseCase().collect { result ->
            result.fold(
                onSuccess = { patients = it },
                onFailure = { errorMessage = it.message ?: "Error al cargar los pacientes" }
            )
        }
        if (errorMessage != null) {
            _uiState.value = UiState.Error(errorMessage!!)
            return
        }

        val cumplimientoPorPaciente = mutableMapOf<String, Int>()
        // Quien revocó el consentimiento no tiene datos visibles: el backend respondería 403.
        for (paciente in patients.filterNot { it.consentimientoRevocado }) {
            getComplianceUseCase(paciente.idPaciente).collect { result ->
                result.fold(
                    onSuccess = { cumplimientoPorPaciente[paciente.idPaciente] = it.porcentajeCumplimiento.toInt() },
                    onFailure = { /* keep defaults */ }
                )
            }
        }

        var invitations: List<Invitation> = emptyList()
        getInvitationsUseCase().collect { result ->
            result.fold(onSuccess = { invitations = it }, onFailure = { /* keep defaults */ })
        }

        _uiState.value = UiState.Success(PatientListData(patients, cumplimientoPorPaciente, invitations))
    }

    fun invitePatient(email: String) {
        viewModelScope.launch {
            _inviteState.value = UiState.Loading
            invitePatientUseCase(email).collect { result ->
                result.fold(
                    onSuccess = {
                        _inviteState.value = UiState.Success(Unit)
                        refreshData()
                    },
                    onFailure = { error ->
                        _inviteState.value = UiState.Error(error.message ?: "Error al invitar")
                    }
                )
            }
        }
    }

    fun cancelInvitation(idInvitacion: String) {
        viewModelScope.launch {
            cancelInvitationUseCase(idInvitacion).collect { result ->
                result.fold(onSuccess = { refreshData() }, onFailure = { /* el usuario puede reintentar */ })
            }
        }
    }

    fun removePatient(idPaciente: String) {
        viewModelScope.launch {
            removePatientUseCase(idPaciente).collect { result ->
                result.fold(onSuccess = { refreshData() }, onFailure = { /* el usuario puede reintentar */ })
            }
        }
    }
}
