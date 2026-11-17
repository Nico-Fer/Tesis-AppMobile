package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.RegistrationResult
import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

class ConfirmRegistrationUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(email: String, code: String): Result<RegistrationResult> =
        authRepository.confirmRegistration(email, code)

    suspend fun resendCode(email: String): Result<Unit> =
        authRepository.resendCode(email)
}
