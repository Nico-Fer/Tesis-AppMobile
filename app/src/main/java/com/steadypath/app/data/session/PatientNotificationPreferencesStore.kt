package com.steadypath.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.steadypath.app.domain.model.PatientNotificationPreferences
import com.steadypath.app.domain.repository.PatientNotificationPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.patientNotificationPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "patient_notification_preferences"
)

@Singleton
class PatientNotificationPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) : PatientNotificationPreferencesRepository {

    private object Keys {
        val RECORDATORIO_RUTINA = booleanPreferencesKey("recordatorio_rutina_diaria")
        val MENSAJES_PROFESIONAL = booleanPreferencesKey("mensajes_del_profesional")
        val RESUMEN_SEMANAL = booleanPreferencesKey("resumen_semanal")
    }

    override val preferences: Flow<PatientNotificationPreferences> =
        context.patientNotificationPrefsDataStore.data.map { prefs ->
            PatientNotificationPreferences(
                recordatorioRutinaDiaria = prefs[Keys.RECORDATORIO_RUTINA] ?: true,
                mensajesDelProfesional = prefs[Keys.MENSAJES_PROFESIONAL] ?: true,
                resumenSemanal = prefs[Keys.RESUMEN_SEMANAL] ?: false
            )
        }

    override suspend fun setRecordatorioRutinaDiaria(habilitado: Boolean) {
        context.patientNotificationPrefsDataStore.edit { it[Keys.RECORDATORIO_RUTINA] = habilitado }
    }

    override suspend fun setMensajesDelProfesional(habilitado: Boolean) {
        context.patientNotificationPrefsDataStore.edit { it[Keys.MENSAJES_PROFESIONAL] = habilitado }
    }

    override suspend fun setResumenSemanal(habilitado: Boolean) {
        context.patientNotificationPrefsDataStore.edit { it[Keys.RESUMEN_SEMANAL] = habilitado }
    }
}
