package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.PatientTolerance

/**
 * Seam angosto para que la capa de comparación de movimiento lea la tolerancia propia del
 * paciente sin depender de toda la superficie de [AuthRepository] (que lo implementa).
 * Separado por Interface Segregation: así [com.steadypath.app.data.comparison.DtwMovementComparator]
 * se puede testear con un fake de un solo método.
 */
interface PatientToleranceRepository {
    /** La tolerancia calibrada por el paciente autenticado para un ejercicio, si la tiene. */
    suspend fun getOwnTolerance(idEjercicio: String): Result<PatientTolerance>
}
