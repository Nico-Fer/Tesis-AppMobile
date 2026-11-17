package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.AssignedProfessional
import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

class GetMyProfessionalUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<AssignedProfessional> = authRepository.getMyProfessional()
}
