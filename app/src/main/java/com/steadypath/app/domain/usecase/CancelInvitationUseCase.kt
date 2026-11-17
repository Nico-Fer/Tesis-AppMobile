package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.repository.MedicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CancelInvitationUseCase @Inject constructor(
    private val medicRepository: MedicRepository
) {
    operator fun invoke(idInvitacion: String): Flow<Result<Unit>> =
        medicRepository.cancelInvitation(idInvitacion)
}
