package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Solo el profesional a cargo de la rutina; un paciente recibe 403. */
class SetRoutineActiveUseCase @Inject constructor(
    private val routineRepository: RoutineRepository
) {
    operator fun invoke(idRutina: String, activa: Boolean): Flow<Result<Routine>> =
        routineRepository.setRoutineActive(idRutina, activa)
}
