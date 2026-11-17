package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.RoutineCompliance
import com.steadypath.app.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRoutineComplianceUseCase @Inject constructor(
    private val routineRepository: RoutineRepository
) {
    operator fun invoke(idRutina: String): Flow<Result<RoutineCompliance>> =
        routineRepository.getRoutineCompliance(idRutina)

    fun forPatient(
        idPaciente: String,
        soloActivas: Boolean = true
    ): Flow<Result<List<RoutineCompliance>>> =
        routineRepository.getPatientRoutineCompliance(idPaciente, soloActivas)
}
