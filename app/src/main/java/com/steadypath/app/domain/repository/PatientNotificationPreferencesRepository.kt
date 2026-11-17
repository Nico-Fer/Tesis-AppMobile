package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.PatientNotificationPreferences
import kotlinx.coroutines.flow.Flow

interface PatientNotificationPreferencesRepository {
    val preferences: Flow<PatientNotificationPreferences>
    suspend fun setRecordatorioRutinaDiaria(habilitado: Boolean)
    suspend fun setMensajesDelProfesional(habilitado: Boolean)
    suspend fun setResumenSemanal(habilitado: Boolean)
}
