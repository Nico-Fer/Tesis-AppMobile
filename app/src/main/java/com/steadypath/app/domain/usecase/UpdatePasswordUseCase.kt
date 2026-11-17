package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

class UpdatePasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(oldPassword: String, newPassword: String): Result<Unit> =
        authRepository.updatePassword(oldPassword, newPassword)
}
