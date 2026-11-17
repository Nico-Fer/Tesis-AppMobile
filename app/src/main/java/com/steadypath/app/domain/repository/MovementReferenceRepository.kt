package com.steadypath.app.domain.repository

/**
 * Subida de referencias de movimiento DTW al backend (una por paciente+ejercicio, upsert).
 * La lectura vive en [ReferenceRepository]: el comparador no sabe de dónde salió la referencia.
 */
interface MovementReferenceRepository {

    /** El paciente autenticado guarda su propia referencia (PUT, reemplaza la anterior). */
    suspend fun uploadOwn(exerciseId: String, referenceJson: String): Result<Unit>

    /** El profesional guarda la referencia de un paciente a su cargo. */
    suspend fun uploadForPatient(
        idPaciente: String,
        exerciseId: String,
        referenceJson: String
    ): Result<Unit>
}
