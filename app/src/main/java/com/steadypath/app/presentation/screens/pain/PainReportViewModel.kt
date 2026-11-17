package com.steadypath.app.presentation.screens.pain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.SesionEnEsperaException
import com.steadypath.app.domain.repository.SessionResultInput
import com.steadypath.app.domain.usecase.SaveSessionUseCase
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.session.RoutineSessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PainReportViewModel @Inject constructor(
    private val saveSessionUseCase: SaveSessionUseCase,
    private val routineSessionTracker: RoutineSessionTracker
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    /** Avisos no bloqueantes; la pantalla los muestra como Toast (sobrevive a la navegación). */
    private val _userMessage = MutableSharedFlow<String>()
    val userMessage: SharedFlow<String> = _userMessage.asSharedFlow()

    fun submitPainReport(escalaEVA: Int) {
        routineSessionTracker.attachEva(escalaEVA)
        proceed()
    }

    /** Saltar deja el resultado con escalaEVA = null; reps y tiempo medidos no se pierden. */
    fun skip() {
        proceed()
    }

    private fun proceed() {
        viewModelScope.launch {
            if (routineSessionTracker.isActive) {
                val next = routineSessionTracker.advance()
                if (next != null) {
                    _navigationEvent.emit(NavigationEvent.NextExercise(next.idEjercicio))
                    return@launch
                }
            }
            saveAndFinish()
        }
    }

    /** Rutina terminada (o ejercicio suelto): un único POST con todos los resultados medidos. */
    private suspend fun saveAndFinish() {
        val resultados = routineSessionTracker.pendingResults
        val totalMinutes = routineSessionTracker.totalMinutes
        if (resultados.isEmpty()) {
            // Nada medido (p. ej. proceso reiniciado): no hay sesión que guardar.
            routineSessionTracker.clear()
            _navigationEvent.emit(NavigationEvent.RoutineFinished(totalMinutes))
            return
        }
        _uiState.value = UiState.Loading
        saveSessionUseCase(
            idRutina = routineSessionTracker.currentRoutineId,
            fechaHoraInicio = routineSessionTracker.startedAtIso,
            duracionMinutos = routineSessionTracker.totalDurationMinutes,
            estado = "Completada",
            resultados = resultados.map {
                SessionResultInput(
                    idEjercicio = it.idEjercicio,
                    // El comparador cuenta reps válidas; no hay métrica aparte de intentos.
                    repeticionesRealizadas = it.repeticiones,
                    repeticionesCorrectas = it.repeticiones,
                    porcentajeCorrectitud = 100.0,
                    escalaEVA = it.escalaEVA
                )
            }
        ).collect { result ->
            result.onFailure { error ->
                _userMessage.emit(
                    if (error is SesionEnEsperaException) {
                        "Sin internet. La sesión quedó guardada y se envía sola cuando vuelva la conexión."
                    } else {
                        "No se pudo guardar la sesión. Revisá tu conexión."
                    }
                )
            }
            _uiState.value = UiState.Success(Unit)
            routineSessionTracker.clear()
            _navigationEvent.emit(NavigationEvent.RoutineFinished(totalMinutes))
        }
    }

    sealed class NavigationEvent {
        data class NextExercise(val exerciseId: String) : NavigationEvent()
        data class RoutineFinished(val durationMinutes: Int) : NavigationEvent()
    }
}
