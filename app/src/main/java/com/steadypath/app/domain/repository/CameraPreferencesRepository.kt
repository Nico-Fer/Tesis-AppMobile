package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.CameraLens
import kotlinx.coroutines.flow.Flow

/**
 * Preferencia persistida de lente de cámara. Es el valor con el que arranca cada flujo;
 * el selector que aparece antes de entrar a la cámara puede apartarse de él para esa vez
 * sin modificarlo.
 *
 * Se guarda por rol porque los predeterminados razonables difieren: el paciente se graba a
 * sí mismo y necesita ver la pantalla, el profesional sostiene el teléfono y apunta a otra
 * persona.
 */
interface CameraPreferencesRepository {
    val lentePaciente: Flow<CameraLens>
    val lenteProfesional: Flow<CameraLens>
    suspend fun setLentePaciente(value: CameraLens)
    suspend fun setLenteProfesional(value: CameraLens)
}
