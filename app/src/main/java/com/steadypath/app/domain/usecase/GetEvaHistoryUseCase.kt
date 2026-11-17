package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.PainRecord
import com.steadypath.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetEvaHistoryUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(idPaciente: String): Flow<Result<List<PainRecord>>> {
        return sessionRepository.getEvaHistory(idPaciente)
    }
}
