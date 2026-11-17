package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.repository.AuthRepository
import javax.inject.Inject

class RejectInvitationUseCase @Inject constructor(
    private val authRepository: AuthRepository
) {
    suspend operator fun invoke(idInvitacion: String): Result<Invitation> =
        authRepository.rejectInvitation(idInvitacion)
}
