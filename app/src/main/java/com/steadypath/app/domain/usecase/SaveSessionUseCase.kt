package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.Session
import com.steadypath.app.domain.repository.SessionRepository
import com.steadypath.app.domain.repository.SessionResultInput
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SaveSessionUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(
        idRutina: String?,
        fechaHoraInicio: String?,
        duracionMinutos: Double,
        estado: String,
        resultados: List<SessionResultInput>
    ): Flow<Result<Session>> {
        return sessionRepository.saveSession(idRutina, fechaHoraInicio, duracionMinutos, estado, resultados)
    }
}
