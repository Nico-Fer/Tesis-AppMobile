package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.repository.MedicRepository
import com.steadypath.app.domain.repository.RoutineExerciseInput
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** El paciente debe estar asignado al profesional del token; si no, 403. */
class CreateRoutineUseCase @Inject constructor(
    private val medicRepository: MedicRepository
) {
    operator fun invoke(
        idPaciente: String,
        nombreRutina: String,
        diasAsignados: List<DiaSemana>,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<Routine>> =
        medicRepository.createRoutine(idPaciente, nombreRutina, diasAsignados, ejercicios)
}
