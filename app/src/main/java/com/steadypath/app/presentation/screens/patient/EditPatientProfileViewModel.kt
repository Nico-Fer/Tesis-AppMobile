package com.steadypath.app.presentation.screens.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.usecase.RestoreSessionUseCase
import com.steadypath.app.domain.usecase.UpdatePatientProfileUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditPatientProfileViewModel @Inject constructor(
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val updatePatientProfileUseCase: UpdatePatientProfileUseCase
) : ViewModel() {

    val nombre = MutableStateFlow("")
    val telefono = MutableStateFlow("")
    val email = MutableStateFlow("")

    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val saveState: StateFlow<UiState<Unit>> = _saveState.asStateFlow()

    private val _profileSaved = MutableStateFlow(false)
    val profileSaved: StateFlow<Boolean> = _profileSaved.asStateFlow()

    fun loadProfile() {
        viewModelScope.launch {
            // Se refresca contra el servidor: la sesión local puede estar desactualizada.
            val user = restoreSessionUseCase() ?: return@launch
            nombre.value = user.nombre
            telefono.value = user.telefono.orEmpty()
            email.value = user.email
        }
    }

    fun onNombreChange(value: String) { nombre.value = value }
    fun onTelefonoChange(value: String) { telefono.value = value }

    fun saveProfile() {
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            val result = updatePatientProfileUseCase(
                nombre.value.trim(),
                telefono.value.trim().ifBlank { null }
            )
            result.fold(
                onSuccess = {
                    _saveState.value = UiState.Success(Unit)
                    _profileSaved.value = true
                },
                onFailure = { error ->
                    _saveState.value = UiState.Error(error.message ?: "Error al guardar el perfil")
                }
            )
        }
    }
}
