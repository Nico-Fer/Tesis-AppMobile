package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.User
import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

class UnlinkProfessionalUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<User> = authRepository.unlinkProfessional()
}
