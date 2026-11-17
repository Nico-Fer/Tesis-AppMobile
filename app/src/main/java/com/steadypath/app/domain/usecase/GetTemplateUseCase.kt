package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.RoutineTemplate
import com.steadypath.app.domain.repository.MedicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTemplateUseCase @Inject constructor(
    private val medicRepository: MedicRepository
) {
    operator fun invoke(idPlantilla: String): Flow<Result<RoutineTemplate>> =
        medicRepository.getTemplate(idPlantilla)
}
