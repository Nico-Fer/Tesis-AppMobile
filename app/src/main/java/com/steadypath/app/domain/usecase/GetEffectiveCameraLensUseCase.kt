package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.CameraLens
import com.steadypath.app.domain.repository.CameraPreferencesRepository
import com.steadypath.app.domain.repository.SessionCameraRepository
import com.steadypath.app.domain.repository.UserSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Lente con el que debe abrirse la cámara: la elección puntual del flujo si la hubo, y si
 * no la preferencia persistida del rol que corresponda.
 *
 * Concentra acá las dos fuentes para que ninguna pantalla tenga que recordar el orden de
 * precedencia ni averiguar el rol por su cuenta.
 */
class GetEffectiveCameraLensUseCase @Inject constructor(
    private val sessionCamera: SessionCameraRepository,
    private val preferences: CameraPreferencesRepository,
    private val session: UserSessionRepository
) {
    operator fun invoke(): Flow<CameraLens> = flow {
        val preferencia = if (esProfesional()) {
            preferences.lenteProfesional
        } else {
            preferences.lentePaciente
        }
        emitAll(
            combine(sessionCamera.lente, preferencia) { puntual, persistida ->
                puntual ?: persistida
            }
        )
    }

    private suspend fun esProfesional() =
        session.get()?.rol.equals(ROL_PROFESIONAL, ignoreCase = true)

    private companion object {
        const val ROL_PROFESIONAL = "Profesional"
    }
}
