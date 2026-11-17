package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.TextScale
import kotlinx.coroutines.flow.Flow

interface TextScalePreferencesRepository {
    val textScale: Flow<TextScale>
    suspend fun setTextScale(value: TextScale)
}
