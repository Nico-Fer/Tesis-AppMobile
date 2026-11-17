package com.steadypath.app.presentation.screens.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.ProvisionData
import com.steadypath.app.domain.model.RegistrationResult
import com.steadypath.app.domain.model.UserRole
import com.steadypath.app.domain.usecase.RegisterUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatientRegistrationViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    val nombre = MutableStateFlow("")
    val email = MutableStateFlow("")
    val contrasena = MutableStateFlow("")
    // Solo visual: el backend acepta un Guid (IdProfesionalAsignado), no un email
    val emailProfesional = MutableStateFlow("")

    /**
     * Aceptación expresa de la política de privacidad. El artículo 5 de la Ley 25.326
     * exige consentimiento expreso y el artículo 7 lo endurece por tratarse de datos
     * sensibles: informar no alcanza, hace falta un acto de la persona. Por eso gatea el
     * botón de registro en lugar de ser un aviso al pie.
     */
    private val _privacidadAceptada = MutableStateFlow(false)
    val privacidadAceptada: StateFlow<Boolean> = _privacidadAceptada.asStateFlow()

    val isFormValid: StateFlow<Boolean> =
        combine(nombre, email, contrasena, _privacidadAceptada) { n, e, c, aceptada ->
            n.isNotBlank() && e.isNotBlank() && c.length >= 8 && aceptada
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onPrivacidadAceptadaChange(value: Boolean) { _privacidadAceptada.value = value }
    fun onNombreChange(value: String) { nombre.value = value }
    fun onEmailChange(value: String) { email.value = value }
    fun onContrasenaChange(value: String) { contrasena.value = value }
    fun onEmailProfesionalChange(value: String) { emailProfesional.value = value }

    fun register() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val data = ProvisionData(
                rol = UserRole.PACIENTE,
                nombre = nombre.value.trim(),
                edad = 30 // TODO: campo de edad en el formulario
            )
            registerUseCase(email.value.trim(), contrasena.value, data).fold(
                onSuccess = { result ->
                    _uiState.value = UiState.Success(Unit)
                    when (result) {
                        is RegistrationResult.Completed ->
                            _navigationEvent.emit(NavigationEvent.RegistrationCompleted)
                        RegistrationResult.NeedsConfirmation ->
                            _navigationEvent.emit(
                                NavigationEvent.NeedsConfirmation(email.value.trim())
                            )
                        RegistrationResult.SignInRequired ->
                            _navigationEvent.emit(NavigationEvent.GoToLogin)
                    }
                },
                onFailure = { error ->
                    _uiState.value = UiState.Error(error.message ?: "Error al registrarse")
                }
            )
        }
    }

    sealed class NavigationEvent {
        data object RegistrationCompleted : NavigationEvent()
        data class NeedsConfirmation(val email: String) : NavigationEvent()
        data object GoToLogin : NavigationEvent()
    }
}
