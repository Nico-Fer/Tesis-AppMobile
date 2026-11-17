package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.ProgressReport
import com.steadypath.app.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import java.util.TimeZone
import javax.inject.Inject

class GetProgressReportUseCase @Inject constructor(
    private val sessionRepository: SessionRepository
) {
    operator fun invoke(idPaciente: String): Flow<Result<ProgressReport>> {
        // El backend agrupa por día según el huso del dispositivo.
        val tzOffsetMinutes = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        return sessionRepository.getProgressReport(idPaciente, tzOffsetMinutes)
    }
}
