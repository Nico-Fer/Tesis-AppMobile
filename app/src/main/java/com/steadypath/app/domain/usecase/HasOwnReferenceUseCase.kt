package com.steadypath.app.domain.usecase

import com.steadypath.app.domain.comparison.ReferenceProvenance
import com.steadypath.app.domain.repository.ReferenceRepository
import javax.inject.Inject

/**
 * Si la persona tiene una referencia **suya** (propia o grabada por su profesional) para el
 * ejercicio. La genérica empaquetada en assets no cuenta: ni siquiera es de esta persona.
 */
class HasOwnReferenceUseCase @Inject constructor(
    private val referenceRepository: ReferenceRepository
) {
    suspend operator fun invoke(exerciseId: String): Boolean {
        referenceRepository.getReference(exerciseId) ?: return false
        return referenceRepository.provenanceOf(exerciseId) != ReferenceProvenance.EMPAQUETADA
    }
}
