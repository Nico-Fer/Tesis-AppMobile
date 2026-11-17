package com.steadypath.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.steadypath.app.domain.model.CameraLens
import com.steadypath.app.domain.repository.CameraPreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.cameraDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "camera_preferences"
)

/**
 * Persiste el lente predeterminado de cada rol. Se guarda el `name` del enum y no su
 * ordinal: reordenar [CameraLens] no debe cambiar el significado de lo ya almacenado.
 * Un valor irreconocible ---de una versión anterior o de una edición manual--- cae al
 * predeterminado en lugar de romper el flujo.
 */
@Singleton
class CameraPreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) : CameraPreferencesRepository {

    private object Keys {
        val LENTE_PACIENTE = stringPreferencesKey("lente_paciente")
        val LENTE_PROFESIONAL = stringPreferencesKey("lente_profesional")
    }

    private fun Preferences.leer(key: Preferences.Key<String>, porDefecto: CameraLens) =
        this[key]?.let { runCatching { CameraLens.valueOf(it) }.getOrNull() } ?: porDefecto

    override val lentePaciente: Flow<CameraLens> =
        context.cameraDataStore.data.map {
            it.leer(Keys.LENTE_PACIENTE, CameraLens.PREDETERMINADO_PACIENTE)
        }

    override val lenteProfesional: Flow<CameraLens> =
        context.cameraDataStore.data.map {
            it.leer(Keys.LENTE_PROFESIONAL, CameraLens.PREDETERMINADO_PROFESIONAL)
        }

    override suspend fun setLentePaciente(value: CameraLens) {
        context.cameraDataStore.edit { it[Keys.LENTE_PACIENTE] = value.name }
    }

    override suspend fun setLenteProfesional(value: CameraLens) {
        context.cameraDataStore.edit { it[Keys.LENTE_PROFESIONAL] = value.name }
    }
}
