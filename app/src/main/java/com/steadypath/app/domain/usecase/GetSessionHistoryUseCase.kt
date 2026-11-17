package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.Session
import com.steadypath.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSessionHistoryUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(idPaciente: String): Flow<Result<List<Session>>> {
        return sessionRepository.getHistory(idPaciente)
    }
}
