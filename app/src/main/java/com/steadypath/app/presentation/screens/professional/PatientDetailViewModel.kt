package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.Compliance
import com.steadypath.app.domain.model.PainRecord
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.Session
import com.steadypath.app.domain.usecase.GetComplianceUseCase
import com.steadypath.app.domain.usecase.GetEvaHistoryUseCase
import com.steadypath.app.domain.usecase.GetPatientRoutinesUseCase
import com.steadypath.app.domain.usecase.GetSessionHistoryUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val HISTORIAL_RECIENTE_LIMITE = 10

data class PatientDetailData(
    val rutinasActivas: List<Routine> = emptyList(),
    val compliance: Compliance? = null,
    val totalSesiones: Int = 0,
    val historialReciente: List<Session> = emptyList(),
    val evolucionDolor: List<PainRecord> = emptyList()
)

@HiltViewModel
class PatientDetailViewModel @Inject constructor(
    private val getPatientRoutinesUseCase: GetPatientRoutinesUseCase,
    private val getComplianceUseCase: GetComplianceUseCase,
    private val getSessionHistoryUseCase: GetSessionHistoryUseCase,
    private val getEvaHistoryUseCase: GetEvaHistoryUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<PatientDetailData>>(UiState.Loading)
    val uiState: StateFlow<UiState<PatientDetailData>> = _uiState.asStateFlow()

    fun loadPatient(idPaciente: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading

            var data = PatientDetailData()

            getPatientRoutinesUseCase(idPaciente, soloActivas = true).collect { result ->
                result.fold(
                    onSuccess = { rutinas -> data = data.copy(rutinasActivas = rutinas) },
                    onFailure = { /* keep defaults */ }
                )
            }

            getComplianceUseCase(idPaciente).collect { result ->
                result.fold(
                    onSuccess = { compliance -> data = data.copy(compliance = compliance) },
                    onFailure = { /* keep defaults */ }
                )
            }

            getSessionHistoryUseCase(idPaciente).collect { result ->
                result.fold(
                    onSuccess = { sessions ->
                        data = data.copy(
                            totalSesiones = sessions.size,
                            historialReciente = sessions.take(HISTORIAL_RECIENTE_LIMITE)
                        )
                    },
                    onFailure = { /* keep defaults */ }
                )
            }

            getEvaHistoryUseCase(idPaciente).collect { result ->
                result.fold(
                    onSuccess = { registros ->
                        data = data.copy(evolucionDolor = registros.takeLast(HISTORIAL_RECIENTE_LIMITE))
                    },
                    onFailure = { /* keep defaults */ }
                )
            }

            _uiState.value = UiState.Success(data)
        }
    }
}
