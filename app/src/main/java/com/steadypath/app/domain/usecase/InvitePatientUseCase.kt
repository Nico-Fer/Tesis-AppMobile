package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.repository.MedicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class InvitePatientUseCase @Inject constructor(
    private val medicRepository: MedicRepository
) {
    operator fun invoke(email: String): Flow<Result<Invitation>> =
        medicRepository.inviteByEmail(email)
}
