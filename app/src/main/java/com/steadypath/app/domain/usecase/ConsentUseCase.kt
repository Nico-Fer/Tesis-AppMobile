package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.repository.AuthRepository
import com.steadypath.app.domain.repository.UserSessionRepository
import javax.inject.Inject

/**
 * Revocar y volver a dar el consentimiento sin dar de baja la cuenta (RF-48, Ley 25.326,
 * art. 5). Revocar bloquea, no borra: los datos se conservan, el profesional deja de verlos
 * y no se registran sesiones nuevas.
 */
class ConsentUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userSessionRepository: UserSessionRepository
) {
    suspend fun revoke(): Result<User> = authRepository.revokeConsent()

    suspend fun grant(): Result<User> = authRepository.grantConsent()

    /** Según la identidad guardada, que /me refresca en cada arranque. */
    suspend fun isRevoked(): Boolean = userSessionRepository.get()?.consentimientoRevocado == true
}
