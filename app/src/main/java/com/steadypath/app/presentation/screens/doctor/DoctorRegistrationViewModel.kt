package com.steadypath.app.presentation.screens.doctor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.ProvisionData
import com.steadypath.app.domain.model.RegistrationResult
import com.steadypath.app.domain.model.TiposProfesional
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
class DoctorRegistrationViewModel @Inject constructor(
    private val registerUseCase: RegisterUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val uiState: StateFlow<UiState<Unit>> = _uiState.asStateFlow()

    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent: SharedFlow<NavigationEvent> = _navigationEvent.asSharedFlow()

    val nombre = MutableStateFlow("")
    val tipoProfesional = MutableStateFlow(TiposProfesional.OPCIONES.first())
    val matricula = MutableStateFlow("")
    val institucion = MutableStateFlow("")
    val email = MutableStateFlow("")
    val contrasena = MutableStateFlow("")
    val certChecked = MutableStateFlow(false)

    val requiereMatricula: StateFlow<Boolean> = tipoProfesional
        .combine(certChecked) { tipo, _ -> tipo != TiposProfesional.FAMILIAR }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isFormValid: StateFlow<Boolean> = combine(
        nombre, tipoProfesional, matricula, email, contrasena, certChecked
    ) { values ->
        val (n, tipo, m, e, c) = values.map { it as? String ?: "" }
        val cert = values[5] as? Boolean ?: false
        val matriculaOk = tipo == TiposProfesional.FAMILIAR || m.isNotBlank()
        n.isNotBlank() && tipo.isNotBlank() && matriculaOk &&
                e.isNotBlank() && c.length >= 8 && cert
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onNombreChange(value: String) { nombre.value = value }
    fun onTipoProfesionalChange(value: String) { tipoProfesional.value = value }
    fun onMatriculaChange(value: String) { matricula.value = value }
    fun onInstitucionChange(value: String) { institucion.value = value }
    fun onEmailChange(value: String) { email.value = value }
    fun onContrasenaChange(value: String) { contrasena.value = value }
    fun onCertCheckedChange(value: Boolean) { certChecked.value = value }

    fun register() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            val data = ProvisionData(
                rol = UserRole.PROFESIONAL,
                nombre = nombre.value.trim(),
                tipoProfesional = tipoProfesional.value,
                matricula = matricula.value.trim().takeIf { it.isNotBlank() && tipoProfesional.value != TiposProfesional.FAMILIAR },
                institucion = institucion.value.trim().takeIf { it.isNotBlank() }
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
