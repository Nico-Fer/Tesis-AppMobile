package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.PatientSummary
import com.steadypath.app.domain.repository.MedicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Pacientes a cargo del profesional del token. */
class GetMyPatientsUseCase @Inject constructor(
    private val medicRepository: MedicRepository
) {
    operator fun invoke(): Flow<Result<List<PatientSummary>>> =
        medicRepository.getMyPatients()
}
