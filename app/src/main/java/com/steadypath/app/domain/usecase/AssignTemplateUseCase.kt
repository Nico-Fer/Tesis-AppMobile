package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.repository.MedicRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Crea una rutina real para el paciente indicado, copiando los ejercicios de la plantilla. */
class AssignTemplateUseCase @Inject constructor(
    private val medicRepository: MedicRepository
) {
    operator fun invoke(
        idPlantilla: String,
        idPaciente: String,
        diasAsignados: List<DiaSemana>
    ): Flow<Result<Routine>> =
        medicRepository.assignTemplate(idPlantilla, idPaciente, diasAsignados)
}
