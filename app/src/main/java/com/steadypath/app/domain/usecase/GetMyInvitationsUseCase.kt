package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

class GetMyInvitationsUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(): Result<List<Invitation>> = authRepository.getMyInvitations()
}
