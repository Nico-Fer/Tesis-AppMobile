package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.RoutineTemplate
import com.steadypath.app.domain.repository.MedicRepository
import com.steadypath.app.domain.repository.RoutineExerciseInput
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Reemplaza por completo el nombre y los ejercicios de una plantilla. */
class EditTemplateUseCase @Inject constructor(
    private val medicRepository: MedicRepository
) {
    operator fun invoke(
        idPlantilla: String,
        nombreRutina: String,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<RoutineTemplate>> =
        medicRepository.editTemplate(idPlantilla, nombreRutina, ejercicios)
}
