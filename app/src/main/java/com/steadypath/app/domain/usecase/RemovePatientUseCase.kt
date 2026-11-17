package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.repository.MedicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RemovePatientUseCase @Inject constructor(
    private val medicRepository: MedicRepository
) {
    operator fun invoke(idPaciente: String): Flow<Result<Unit>> =
        medicRepository.removePatient(idPaciente)
}
