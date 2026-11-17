package com.steadypath.app.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.CameraLens
import com.steadypath.app.domain.repository.CameraPreferencesRepository
import com.steadypath.app.domain.repository.UserSessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lee y escribe la preferencia de cámara del rol que corresponda al usuario de la sesión.
 *
 * El rol se resuelve una sola vez y se memoriza: la pantalla no puede cambiar de usuario
 * mientras está abierta, y releerlo en cada escritura obligaría a una consulta por toque.
 */
@HiltViewModel
class CameraPreferenceViewModel @Inject constructor(
    private val preferences: CameraPreferencesRepository,
    private val session: UserSessionRepository
) : ViewModel() {

    private val esProfesional = MutableStateFlow<Boolean?>(null)

    val lente: StateFlow<CameraLens> = flow {
        val profesional = session.get()?.rol.equals(ROL_PROFESIONAL, ignoreCase = true)
        esProfesional.value = profesional
        emitAll(if (profesional) preferences.lenteProfesional else preferences.lentePaciente)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CameraLens.PREDETERMINADO_PACIENTE
    )

    fun elegir(lens: CameraLens) {
        viewModelScope.launch {
            val profesional = esProfesional.value
                ?: session.get()?.rol.equals(ROL_PROFESIONAL, ignoreCase = true)
            if (profesional) preferences.setLenteProfesional(lens)
            else preferences.setLentePaciente(lens)
        }
    }

    private companion object {
        const val ROL_PROFESIONAL = "Profesional"
    }
}
