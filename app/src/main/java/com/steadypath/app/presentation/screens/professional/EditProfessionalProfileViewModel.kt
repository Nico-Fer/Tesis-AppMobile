package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.usecase.RestoreSessionUseCase
import com.steadypath.app.domain.usecase.UpdateProfessionalProfileUseCase
import com.steadypath.app.presentation.common.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditProfessionalProfileViewModel @Inject constructor(
    private val restoreSessionUseCase: RestoreSessionUseCase,
    private val updateProfessionalProfileUseCase: UpdateProfessionalProfileUseCase
) : ViewModel() {

    val nombre = MutableStateFlow("")
    val tipoProfesional = MutableStateFlow("")
    val matricula = MutableStateFlow("")
    val institucion = MutableStateFlow("")
    val email = MutableStateFlow("")

    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Success(Unit))
    val saveState: StateFlow<UiState<Unit>> = _saveState.asStateFlow()

    private val _profileSaved = MutableStateFlow(false)
    val profileSaved: StateFlow<Boolean> = _profileSaved.asStateFlow()

    fun loadProfile() {
        viewModelScope.launch {
            // Se refresca contra el servidor: la sesión local puede estar desactualizada
            // si el perfil se editó desde otro dispositivo o antes de este login.
            val user = restoreSessionUseCase() ?: return@launch
            nombre.value = user.nombre
            tipoProfesional.value = user.tipoProfesional.orEmpty()
            matricula.value = user.matricula.orEmpty()
            institucion.value = user.institucion.orEmpty()
            email.value = user.email
        }
    }

    fun onNombreChange(value: String) { nombre.value = value }
    fun onTipoProfesionalChange(value: String) { tipoProfesional.value = value }
    fun onMatriculaChange(value: String) { matricula.value = value }
    fun onInstitucionChange(value: String) { institucion.value = value }

    fun saveProfile() {
        viewModelScope.launch {
            _saveState.value = UiState.Loading
            val result = updateProfessionalProfileUseCase(
                nombre.value.trim(),
                tipoProfesional.value.trim().ifBlank { null },
                matricula.value.trim().ifBlank { null },
                institucion.value.trim().ifBlank { null }
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
