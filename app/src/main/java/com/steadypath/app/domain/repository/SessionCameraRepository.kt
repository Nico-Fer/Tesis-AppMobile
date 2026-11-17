package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.CameraLens
import kotlinx.coroutines.flow.StateFlow

/**
 * Elección puntual de lente para el flujo en curso, sin persistir.
 *
 * `null` significa que no hubo elección para esta vez y corresponde usar la preferencia
 * persistida del rol ([CameraPreferencesRepository]).
 */
interface SessionCameraRepository {
    val lente: StateFlow<CameraLens?>
    fun elegir(lens: CameraLens)
    fun limpiar()
}
