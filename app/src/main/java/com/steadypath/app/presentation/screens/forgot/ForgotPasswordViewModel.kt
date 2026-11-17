package com.steadypath.app.presentation.screens.forgot

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.steadypath.app.domain.usecase.ResetPasswordUseCase
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.navigation.ForgotPassword
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Recuperación de la contraseña olvidada (RF-03), en dos pasos: el email para
 * que Cognito mande el código, y el código con la contraseña nueva.
 */
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val resetPasswordUseCase: ResetPasswordUseCase
) : ViewModel() {

    enum class Paso { EMAIL, CODIGO, LISTO }

    /** Arranca con el email que la persona ya había escrito en el login. */
    val email = MutableStateFlow(savedStateHandle.toRoute<ForgotPassword>().email)
    val code = MutableStateFlow("")
    val newPassword = MutableStateFlow("")
    val confirmPassword = MutableStateFlow("")

    private val _paso = MutableStateFlow(Paso.EMAIL)
    val paso: StateFlow<Paso> = _paso.asStateFlow()

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage: StateFlow<String?> = _infoMessage.asStateFlow()

    fun onEmailChange(value: String) { email.value = value }
    fun onCodeChange(value: String) { code.value = value }
    fun onNewPasswordChange(value: String) { newPassword.value = value }
    fun onConfirmPasswordChange(value: String) { confirmPassword.value = value }

    fun requestCode() {
        if (!ValidacionRecuperacion.emailValido(email.value)) {
            _uiState.value = UiState.Error("Ingresá un email válido")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _infoMessage.value = null
            resetPasswordUseCase.requestCode(email.value.trim()).fold(
                onSuccess = {
                    _uiState.value = UiState.Success(Unit)
                    if (_paso.value == Paso.CODIGO) {
                        _infoMessage.value = "Te mandamos un código nuevo"
                    }
                    _paso.value = Paso.CODIGO
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "No se pudo enviar el código")
                }
            )
        }
    }

    fun confirm() {
        ValidacionRecuperacion.problema(code.value, newPassword.value, confirmPassword.value)?.let {
            _uiState.value = UiState.Error(it)
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            _infoMessage.value = null
            resetPasswordUseCase(email.value.trim(), code.value.trim(), newPassword.value).fold(
                onSuccess = {
                    _uiState.value = UiState.Success(Unit)
                    _paso.value = Paso.LISTO
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "No se pudo cambiar la contraseña")
                }
            )
        }
    }

    /** Volver a escribir el email, por ejemplo si estaba mal tipeado. */
    fun changeEmail() {
        _uiState.value = UiState.Success(Unit)
        _infoMessage.value = null
        code.value = ""
        _paso.value = Paso.EMAIL
    }
}
