package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.Tolerance
import com.steadypath.app.domain.repository.MedicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SetToleranceUseCase @Inject constructor(
    private val medicRepository: MedicRepository
) {
    operator fun invoke(
        idPaciente: String,
        idEjercicio: String,
        anguloMinimo: Double,
        anguloMaximo: Double
    ): Flow<Result<Tolerance>> =
        medicRepository.setTolerance(idPaciente, idEjercicio, anguloMinimo, anguloMaximo)
}
