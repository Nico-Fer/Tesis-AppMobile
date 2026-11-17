package com.steadypath.app.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.steadypath.app.domain.model.TextScale
import com.steadypath.app.domain.repository.TextScalePreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.textScaleDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "text_scale_preferences"
)

@Singleton
class TextScalePreferencesStore @Inject constructor(
    @ApplicationContext private val context: Context
) : TextScalePreferencesRepository {

    private object Keys {
        val TEXT_SCALE = stringPreferencesKey("text_scale")
    }

    override val textScale: Flow<TextScale> =
        context.textScaleDataStore.data.map { prefs ->
            prefs[Keys.TEXT_SCALE]?.let { saved ->
                runCatching { TextScale.valueOf(saved) }.getOrNull()
            } ?: TextScale.NORMAL
        }

    override suspend fun setTextScale(value: TextScale) {
        context.textScaleDataStore.edit { it[Keys.TEXT_SCALE] = value.name }
    }
}
