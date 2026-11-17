package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

/** Recuperación de la contraseña olvidada (RF-03): pedir el código y fijar la nueva. */
class ResetPasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend fun requestCode(email: String): Result<Unit> =
        authRepository.requestPasswordReset(email)

    suspend operator fun invoke(email: String, code: String, newPassword: String): Result<Unit> =
        authRepository.confirmPasswordReset(email, code, newPassword)
}
