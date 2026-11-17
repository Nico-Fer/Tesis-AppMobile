package com.steadypath.app.presentation.screens.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.AssignedProfessional
import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.repository.SyncStatusRepository
import com.steadypath.app.domain.repository.UserSessionRepository
import com.steadypath.app.domain.usecase.GetMyProfessionalUseCase
import com.steadypath.app.domain.usecase.SignOutUseCase
import com.steadypath.app.domain.usecase.UnlinkProfessionalUseCase
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.session.RoutineSessionTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatientProfileViewModel @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    private val getMyProfessionalUseCase: GetMyProfessionalUseCase,
    private val unlinkProfessionalUseCase: UnlinkProfessionalUseCase,
    private val signOutUseCase: SignOutUseCase,
    private val routineSessionTracker: RoutineSessionTracker,
    sincronizacion: SyncStatusRepository
) : ViewModel() {

    /** Para advertir en la confirmación de cierre de sesión qué se perdería. */
    val cambiosSinSincronizar: StateFlow<Int> = sincronizacion.estado
        .map { it.pendientes }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _professional = MutableStateFlow<AssignedProfessional?>(null)
    val professional: StateFlow<AssignedProfessional?> = _professional.asStateFlow()

    private val _unlinkState = MutableStateFlow<UiState<Unit>?>(null)
    val unlinkState: StateFlow<UiState<Unit>?> = _unlinkState.asStateFlow()

    private val _signOutState = MutableStateFlow<UiState<Unit>?>(null)
    val signOutState: StateFlow<UiState<Unit>?> = _signOutState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    fun loadProfile() {
        viewModelScope.launch {
            _user.value = userSessionRepository.get()
            getMyProfessionalUseCase().onSuccess { _professional.value = it }
        }
    }

    fun unlinkProfessional() {
        viewModelScope.launch {
            _unlinkState.value = UiState.Loading
            unlinkProfessionalUseCase().fold(
                onSuccess = {
                    _unlinkState.value = UiState.Success(Unit)
                    _professional.value = AssignedProfessional(
                        tieneProfesionalAsignado = false,
                        idProfesional = null,
                        nombre = null,
                        tipoProfesional = null
                    )
                },
                onFailure = { error ->
                    _unlinkState.value = UiState.Error(error.message ?: "Error al desvincularte")
                }
            )
        }
    }

    fun signOut() {
        if (_signOutState.value is UiState.Loading) return
        viewModelScope.launch {
            _signOutState.value = UiState.Loading
            signOutUseCase().fold(
                onSuccess = {
                    // La rutina en curso es del usuario que sale; no debe filtrarse al próximo.
                    routineSessionTracker.clear()
                    _navigationEvent.emit(NavigationEvent.SignedOut)
                },
                onFailure = { error ->
                    _signOutState.value = UiState.Error(error.message ?: "No se pudo cerrar sesión")
                }
            )
        }
    }

    sealed class NavigationEvent {
        data object SignedOut : NavigationEvent()
    }
}
