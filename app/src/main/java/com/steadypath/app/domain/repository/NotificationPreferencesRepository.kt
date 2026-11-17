package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.NotificationPreferences
import kotlinx.coroutines.flow.Flow

interface NotificationPreferencesRepository {
    val preferences: Flow<NotificationPreferences>
    suspend fun setAlertasDolorAlto(habilitado: Boolean)
    suspend fun setAlertasPacientesInactivos(habilitado: Boolean)
    suspend fun setResumenSemanal(habilitado: Boolean)
}
