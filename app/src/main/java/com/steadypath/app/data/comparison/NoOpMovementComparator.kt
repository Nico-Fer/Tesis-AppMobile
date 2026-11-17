package com.steadypath.app.data.comparison

import com.steadypath.app.domain.comparison.ComparisonResult
import com.steadypath.app.domain.comparison.MovementComparator
import com.steadypath.app.domain.comparison.MovementStatus
import com.steadypath.app.domain.model.PoseFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Stub de la capa de comparación: deja pasar el stream de poses sin evaluarlas, emitiendo
 * siempre un resultado neutro. Útil como fallback de desarrollo para aislar el pipeline de
 * cámara → inferencia → UI de la lógica de comparación ([DtwMovementComparator]).
 * No agregar lógica de comparación aquí: este tipo debe seguir siendo el no-op.
 */
class NoOpMovementComparator @Inject constructor() : MovementComparator {
    override fun compare(exerciseId: String, poses: Flow<PoseFrame>): Flow<ComparisonResult> =
        poses.map { ComparisonResult(status = MovementStatus.Unknown, repDelta = 0) }
}
