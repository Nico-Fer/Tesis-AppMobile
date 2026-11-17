package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.RoutineTemplate
import com.steadypath.app.domain.repository.MedicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Las plantillas de rutina del profesional autenticado. */
class GetTemplatesUseCase @Inject constructor(
    private val medicRepository: MedicRepository
) {
    operator fun invoke(): Flow<Result<List<RoutineTemplate>>> =
        medicRepository.getTemplates()
}
