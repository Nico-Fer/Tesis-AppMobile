package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.repository.UserSessionRepository
import com.steadypath.app.domain.usecase.SignOutUseCase
import com.steadypath.app.presentation.common.UiState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ProfessionalProfileViewModel @Inject constructor(
    private val userSessionRepository: UserSessionRepository,
    private val signOutUseCase: SignOutUseCase
) : ViewModel() {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user.asStateFlow()

    private val _signOutState = MutableStateFlow<UiState<Unit>?>(null)
    val signOutState: StateFlow<UiState<Unit>?> = _signOutState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    fun loadProfile() {
        viewModelScope.launch {
            _user.value = userSessionRepository.get()
        }
    }

    fun signOut() {
        if (_signOutState.value is UiState.Loading) return
        viewModelScope.launch {
            _signOutState.value = UiState.Loading
            signOutUseCase().fold(
                onSuccess = { _navigationEvent.emit(NavigationEvent.SignedOut) },
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
