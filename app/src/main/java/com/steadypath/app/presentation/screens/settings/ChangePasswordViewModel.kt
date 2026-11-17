package com.steadypath.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.usecase.UpdatePasswordUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val LONGITUD_MINIMA = 8

/** Compartido entre paciente y profesional: cambiar la contraseña no tiene lógica especifica de rol. */
@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val updatePasswordUseCase: UpdatePasswordUseCase
) : ViewModel() {

    val currentPassword = MutableStateFlow("")
    val newPassword = MutableStateFlow("")
    val confirmPassword = MutableStateFlow("")

    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val saveState: StateFlow<UiState<Unit>> = _saveState.asStateFlow()

    private val _passwordChanged = MutableStateFlow(false)
    val passwordChanged: StateFlow<Boolean> = _passwordChanged.asStateFlow()

    fun onCurrentPasswordChange(value: String) { currentPassword.value = value }
    fun onNewPasswordChange(value: String) { newPassword.value = value }
    fun onConfirmPasswordChange(value: String) { confirmPassword.value = value }

    fun esFormularioValido(): Boolean =
        currentPassword.value.isNotBlank() &&
            newPassword.value.length >= LONGITUD_MINIMA &&
            newPassword.value == confirmPassword.value

    fun changePassword() {
        if (newPassword.value != confirmPassword.value) {
            _saveState.value = UiState.Error("Las contraseñas nuevas no coinciden")
            return
        }
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            updatePasswordUseCase(currentPassword.value, newPassword.value).fold(
                onSuccess = {
                    _saveState.value = UiState.Success(Unit)
                    _passwordChanged.value = true
                },
                onFailure = { error ->
                    _saveState.value = UiState.Error(error.message ?: "Error al cambiar la contraseña")
                }
            )
        }
    }
}
