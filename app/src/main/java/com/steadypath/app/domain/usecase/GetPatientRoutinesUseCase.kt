package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPatientRoutinesUseCase @Inject constructor(
    private val routineRepository: RoutineRepository
) {
    operator fun invoke(
        idPaciente: String,
        soloActivas: Boolean = true
    ): Flow<Result<List<Routine>>> =
        routineRepository.getPatientRoutines(idPaciente, soloActivas)
}
