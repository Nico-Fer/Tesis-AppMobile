package com.steadypath.app.presentation.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.NotProvisionedException
import com.steadypath.app.domain.model.UserNotConfirmedException
import com.steadypath.app.domain.usecase.SignInUseCase
import com.steadypath.app.presentation.common.UiState
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
class LoginViewModel @Inject constructor(
    private val signInUseCase: SignInUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    fun login(email: String, contrasena: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            signInUseCase(email.trim(), contrasena).fold(
                onSuccess = { user ->
                    _uiState.value = UiState.Success(Unit)
                    _navigationEvent.emit(NavigationEvent.LoginSuccess(user.rol))
                },
                onFailure = { error ->
                    when (error) {
                        is UserNotConfirmedException -> {
                            _uiState.value = UiState.Success(Unit)
                            _navigationEvent.emit(NavigationEvent.NeedsConfirmation(email.trim()))
                        }
                        is NotProvisionedException -> _uiState.value = UiState.Error(
                            "Tu cuenta no tiene perfil todavía. Completá el registro desde \"Registrate\"."
                        )
                        else -> _uiState.value =
                            UiState.Error(error.message ?: "Error al iniciar sesión")
                    }
                }
            )
        }
    }

    sealed class NavigationEvent {
        data class LoginSuccess(val rol: String) : NavigationEvent()
        data class NeedsConfirmation(val email: String) : NavigationEvent()
    }
}
