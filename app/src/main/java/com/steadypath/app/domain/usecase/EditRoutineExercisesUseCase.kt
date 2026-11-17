package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.repository.RoutineExerciseInput
import com.steadypath.app.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Reemplaza días y ejercicios de una rutina ya asignada. Solo el profesional a cargo. */
class EditRoutineExercisesUseCase @Inject constructor(
    private val routineRepository: RoutineRepository
) {
    operator fun invoke(
        idRutina: String,
        diasAsignados: List<DiaSemana>,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<Routine>> =
        routineRepository.editRoutineExercises(idRutina, diasAsignados, ejercicios)
}
