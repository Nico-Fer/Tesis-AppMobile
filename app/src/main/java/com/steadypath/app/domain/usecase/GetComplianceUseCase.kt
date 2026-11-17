package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.Compliance
import com.steadypath.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetComplianceUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(idPaciente: String): Flow<Result<Compliance>> {
        return sessionRepository.getCompliance(idPaciente)
    }
}
