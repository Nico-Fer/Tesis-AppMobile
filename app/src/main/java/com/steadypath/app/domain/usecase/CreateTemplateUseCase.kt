package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.RoutineTemplate
import com.steadypath.app.domain.repository.MedicRepository
import com.steadypath.app.domain.repository.RoutineExerciseInput
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class CreateTemplateUseCase @Inject constructor(
    private val medicRepository: MedicRepository
) {
    operator fun invoke(
        nombreRutina: String,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<RoutineTemplate>> =
        medicRepository.createTemplate(nombreRutina, ejercicios)
}
