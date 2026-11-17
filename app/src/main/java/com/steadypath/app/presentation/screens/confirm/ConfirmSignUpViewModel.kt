package com.steadypath.app.presentation.screens.confirm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.steadypath.app.domain.model.RegistrationResult
import com.steadypath.app.domain.usecase.ConfirmRegistrationUseCase
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.navigation.ConfirmSignUp
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
class ConfirmSignUpViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val confirmRegistrationUseCase: ConfirmRegistrationUseCase
) : ViewModel() {

    val email: String = savedStateHandle.toRoute<ConfirmSignUp>().email

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    fun confirm(code: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            confirmRegistrationUseCase(email, code.trim()).fold(
                onSuccess = { result ->
                    _uiState.value = UiState.Success(Unit)
                    when (result) {
                        is RegistrationResult.Completed ->
                            _navigationEvent.emit(
                                NavigationEvent.Completed(result.user.rol)
                            )
                        RegistrationResult.SignInRequired ->
                            _navigationEvent.emit(NavigationEvent.GoToLogin)
                        // confirmRegistration nunca devuelve NeedsConfirmation
                        RegistrationResult.NeedsConfirmation -> Unit
                    }
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Error al confirmar el código")
                }
            )
        }
    }

    fun resendCode() {
        viewModelScope.launch {
            _infoMessage.value = null
            confirmRegistrationUseCase.resendCode(email).fold(
                onSuccess = { _infoMessage.value = "Te mandamos un código nuevo" },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "No se pudo reenviar el código")
                }
            )
        }
    }

    sealed class NavigationEvent {
        data class Completed(val rol: String) : NavigationEvent()
        data object GoToLogin : NavigationEvent()
    }
}
