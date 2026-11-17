package com.steadypath.app.presentation.screens.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.PatientNotificationPreferences
import com.steadypath.app.domain.repository.PatientNotificationPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PatientNotificationSettingsViewModel @Inject constructor(
    private val repository: PatientNotificationPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<PatientNotificationPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PatientNotificationPreferences())

    fun onRecordatorioRutinaDiariaChange(habilitado: Boolean) {
        viewModelScope.launch { repository.setRecordatorioRutinaDiaria(habilitado) }
    }

    fun onMensajesDelProfesionalChange(habilitado: Boolean) {
        viewModelScope.launch { repository.setMensajesDelProfesional(habilitado) }
    }

    fun onResumenSemanalChange(habilitado: Boolean) {
        viewModelScope.launch { repository.setResumenSemanal(habilitado) }
    }
}
