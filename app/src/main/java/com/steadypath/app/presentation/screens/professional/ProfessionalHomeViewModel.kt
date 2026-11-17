package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.model.PatientSummary
import com.steadypath.app.domain.model.Session
import com.steadypath.app.domain.usecase.GetComplianceUseCase
import com.steadypath.app.domain.usecase.GetInvitationsUseCase
import com.steadypath.app.domain.usecase.GetMyPatientsUseCase
import com.steadypath.app.domain.usecase.GetSessionHistoryUseCase
import com.steadypath.app.domain.usecase.SignOutUseCase
import com.steadypath.app.domain.repository.UserSessionRepository
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private const val EVA_ALERTA = 7
private const val DIAS_SIN_ACTIVIDAD_ALERTA = 5

/** Motivo por el que un paciente aparece en "Requieren tu atención". */
sealed class PatientAlert {
    data class HighPain(val eva: Int) : PatientAlert()
    data class Inactive(val dias: Int) : PatientAlert()
}

data class PatientAttentionItem(
    val patient: PatientSummary,
    val alert: PatientAlert
)

data class ProfessionalHomeData(
    val nombreProfesional: String = "",
    val cantidadPacientes: Int = 0,
    val sesionesEstaSemana: Int = 0,
    val cumplimientoPromedio: Int? = null,
    val pacientesQueRequierenAtencion: List<PatientAttentionItem> = emptyList(),
    val invitacionesPendientes: List<Invitation> = emptyList()
)

@HiltViewModel
class ProfessionalHomeViewModel @Inject constructor(
    private val getMyPatientsUseCase: GetMyPatientsUseCase,
    private val getComplianceUseCase: GetComplianceUseCase,
    private val getSessionHistoryUseCase: GetSessionHistoryUseCase,
    private val getInvitationsUseCase: GetInvitationsUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ProfessionalHomeData>>(UiState.Loading)
    val uiState: StateFlow<UiState<ProfessionalHomeData>> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    fun loadHome() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            var pacientes: List<PatientSummary> = emptyList()
            getMyPatientsUseCase().collect { result ->
                result.fold(
                    onSuccess = { pacientes = it },
                    onFailure = {
                        _uiState.value = UiState.Error(it.message ?: "Error al cargar los pacientes")
                    }
                )
            }

            if (_uiState.value is UiState.Error) return@launch

            var sesionesEstaSemana = 0
            val cumplimientos = mutableListOf<Double>()
            val atencion = mutableListOf<PatientAttentionItem>()
            val inicioSemana = LocalDate.now().minusDays(6)

            for (paciente in pacientes) {
                var sesiones: List<Session> = emptyList()
                getSessionHistoryUseCase(paciente.idPaciente).collect { result ->
                    result.fold(onSuccess = { sesiones = it }, onFailure = { /* keep defaults */ })
                }
                sesionesEstaSemana += sesiones.count { sesion ->
                    localDateOf(sesion.fechaHoraInicio)?.let { !it.isBefore(inicioSemana) } == true
                }

                getComplianceUseCase(paciente.idPaciente).collect { result ->
                    result.fold(
                        onSuccess = { cumplimientos.add(it.porcentajeCumplimiento) },
                        onFailure = { /* keep defaults */ }
                    )
                }

                val ultimoEva = sesiones
                    .flatMap { it.resultados }
                    .mapNotNull { it.escalaEVA }
                    .firstOrNull()

                val diasSinActividad = paciente.fechaUltimaSesion
                    ?.let { localDateOf(it) }
                    ?.let { java.time.temporal.ChronoUnit.DAYS.between(it, LocalDate.now()) }

                when {
                    ultimoEva != null && ultimoEva >= EVA_ALERTA ->
                        atencion.add(PatientAttentionItem(paciente, PatientAlert.HighPain(ultimoEva)))
                    diasSinActividad != null && diasSinActividad >= DIAS_SIN_ACTIVIDAD_ALERTA ->
                        atencion.add(PatientAttentionItem(paciente, PatientAlert.Inactive(diasSinActividad.toInt())))
                }
            }

            var invitaciones: List<Invitation> = emptyList()
            getInvitationsUseCase().collect { result ->
                result.fold(onSuccess = { invitaciones = it }, onFailure = { /* keep defaults */ })
            }

            _uiState.value = UiState.Success(
                ProfessionalHomeData(
                    nombreProfesional = userSessionRepository.get()?.nombre ?: "",
                    cantidadPacientes = pacientes.size,
                    sesionesEstaSemana = sesionesEstaSemana,
                    cumplimientoPromedio = cumplimientos.takeIf { it.isNotEmpty() }?.average()?.toInt(),
                    pacientesQueRequierenAtencion = atencion,
                    invitacionesPendientes = invitaciones
                )
            )
        }
    }

    fun signOut() {
        viewModelScope.launch {
            signOutUseCase()
            _navigationEvent.emit(NavigationEvent.SignedOut)
        }
    }

    // fechaHoraInicio/fechaUltimaSesion viajan en UTC: se convierten a fecha local del dispositivo.
    private fun localDateOf(iso: String): LocalDate? =
        runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }
            .getOrElse { runCatching { LocalDate.parse(iso.take(10)) }.getOrNull() }

    sealed class NavigationEvent {
        data object SignedOut : NavigationEvent()
    }
}
