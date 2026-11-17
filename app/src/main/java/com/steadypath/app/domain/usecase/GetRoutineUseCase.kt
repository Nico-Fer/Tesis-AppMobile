package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetRoutineUseCase @Inject constructor(
    private val routineRepository: RoutineRepository
) {
    operator fun invoke(idRutina: String): Flow<Result<Routine>> =
        routineRepository.getRoutine(idRutina)
}
