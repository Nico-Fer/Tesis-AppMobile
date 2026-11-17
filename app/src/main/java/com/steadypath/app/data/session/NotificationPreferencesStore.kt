package com.steadypath.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.steadypath.app.domain.model.NotificationPreferences
import com.steadypath.app.domain.repository.NotificationPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "notification_preferences"
)

@Singleton
class NotificationPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationPreferencesRepository {

    private object Keys {
        val DOLOR_ALTO = booleanPreferencesKey("alertas_dolor_alto")
        val PACIENTES_INACTIVOS = booleanPreferencesKey("alertas_pacientes_inactivos")
        val RESUMEN_SEMANAL = booleanPreferencesKey("resumen_semanal")
    }

    override val preferences: Flow<NotificationPreferences> =
        context.notificationPrefsDataStore.data.map { prefs ->
            NotificationPreferences(
                alertasDolorAlto = prefs[Keys.DOLOR_ALTO] ?: true,
                alertasPacientesInactivos = prefs[Keys.PACIENTES_INACTIVOS] ?: true,
                resumenSemanal = prefs[Keys.RESUMEN_SEMANAL] ?: false
            )
        }

    override suspend fun setAlertasDolorAlto(habilitado: Boolean) {
        context.notificationPrefsDataStore.edit { it[Keys.DOLOR_ALTO] = habilitado }
    }

    override suspend fun setAlertasPacientesInactivos(habilitado: Boolean) {
        context.notificationPrefsDataStore.edit { it[Keys.PACIENTES_INACTIVOS] = habilitado }
    }

    override suspend fun setResumenSemanal(habilitado: Boolean) {
        context.notificationPrefsDataStore.edit { it[Keys.RESUMEN_SEMANAL] = habilitado }
    }
}
