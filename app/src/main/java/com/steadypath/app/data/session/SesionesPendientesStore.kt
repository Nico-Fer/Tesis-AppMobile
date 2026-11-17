package com.steadypath.app.data.session

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.steadypath.app.data.dto.CreateSessionRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sesionesPendientesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "sesiones_pendientes"
)

/** Formato con que la cola anterior guardaba cada sesión sin enviar. */
data class SesionPendiente(
    val idCliente: String,
    val idUsuario: String,
    val solicitud: CreateSessionRequestDto
)

/**
 * Cola de sesiones de la versión anterior, que vivía en DataStore. Hoy la cola de salida es
 * [com.steadypath.app.data.sync.ColaDeOperaciones]; esta clase solo queda para rescatar lo
 * que una instalación ya existente tuviera sin enviar al actualizarse. Nada escribe acá.
 */
@Singleton
class SesionesPendientesStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) : UserScopedCache {

    private val clave = stringPreferencesKey("sesiones")
    private val tipo = object : TypeToken<List<SesionPendiente>>() {}.type

    /** Devuelve lo que había y lo borra, en una sola transacción para no duplicarlo. */
    suspend fun extraerTodas(): List<SesionPendiente> {
        var extraidas: List<SesionPendiente> = emptyList()
        context.sesionesPendientesDataStore.edit { prefs ->
            val json = prefs[clave] ?: return@edit
            extraidas = runCatching { gson.fromJson<List<SesionPendiente>>(json, tipo) }
                .onFailure { Log.e(TAG, "Cola de sesiones anterior ilegible", it) }
                .getOrNull()
                .orEmpty()
            prefs.remove(clave)
        }
        return extraidas
    }

    override suspend fun clear() {
        context.sesionesPendientesDataStore.edit { it.remove(clave) }
    }

    private companion object {
        const val TAG = "SesionesPendientes"
    }
}
