package com.steadypath.app.presentation.screens.professional

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.NotificationPreferences
import com.steadypath.app.domain.repository.NotificationPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val repository: NotificationPreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<NotificationPreferences> = repository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NotificationPreferences())

    fun onAlertasDolorAltoChange(habilitado: Boolean) {
        viewModelScope.launch { repository.setAlertasDolorAlto(habilitado) }
    }

    fun onAlertasPacientesInactivosChange(habilitado: Boolean) {
        viewModelScope.launch { repository.setAlertasPacientesInactivos(habilitado) }
    }

    fun onResumenSemanalChange(habilitado: Boolean) {
        viewModelScope.launch { repository.setResumenSemanal(habilitado) }
    }
}
