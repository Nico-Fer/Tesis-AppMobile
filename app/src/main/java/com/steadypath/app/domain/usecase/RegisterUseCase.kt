package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.ProvisionData
import com.steadypath.app.domain.model.RegistrationResult
import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

class RegisterUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(
        email: String,
        contrasena: String,
        data: ProvisionData
    ): Result<RegistrationResult> = authRepository.register(email, contrasena, data)
}
