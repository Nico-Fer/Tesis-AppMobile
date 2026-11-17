package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.repository.RoutineExerciseInput
import com.steadypath.app.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** El paciente autenticado crea una rutina propia (sin profesional asignador). */
class CreateOwnRoutineUseCase @Inject constructor(
    private val routineRepository: RoutineRepository
) {
    operator fun invoke(
        nombreRutina: String,
        diasAsignados: List<DiaSemana>,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<Routine>> = routineRepository.createOwnRoutine(nombreRutina, diasAsignados, ejercicios)
}
