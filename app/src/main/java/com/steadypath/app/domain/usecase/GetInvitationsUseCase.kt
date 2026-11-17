package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.repository.MedicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Las invitaciones pendientes del profesional autenticado. */
class GetInvitationsUseCase @Inject constructor(
    private val medicRepository: MedicRepository
) {
    operator fun invoke(): Flow<Result<List<Invitation>>> =
        medicRepository.getInvitations()
}
