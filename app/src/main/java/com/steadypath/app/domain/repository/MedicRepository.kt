package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.model.PatientSummary
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.RoutineTemplate
import com.steadypath.app.domain.model.Tolerance
import kotlinx.coroutines.flow.Flow

/** Operaciones del profesional; con token de paciente devuelven 403. */
interface MedicRepository {

    fun getMyPatients(): Flow<Result<List<PatientSummary>>>

    /** Desvincula al paciente de este profesional (no borra su cuenta ni su historial). */
    fun removePatient(idPaciente: String): Flow<Result<Unit>>

    fun createRoutine(
        idPaciente: String,
        nombreRutina: String,
        diasAsignados: List<DiaSemana>,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<Routine>>

    fun setTolerance(
        idPaciente: String,
        idEjercicio: String,
        anguloMinimo: Double,
        anguloMaximo: Double
    ): Flow<Result<Tolerance>>

    // --- Plantillas de rutina ---

    fun createTemplate(
        nombreRutina: String,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<RoutineTemplate>>

    fun getTemplates(): Flow<Result<List<RoutineTemplate>>>

    fun getTemplate(idPlantilla: String): Flow<Result<RoutineTemplate>>

    fun editTemplate(
        idPlantilla: String,
        nombreRutina: String,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<RoutineTemplate>>

    /** Crea una rutina real para el paciente indicado, copiando los ejercicios de la plantilla. */
    fun assignTemplate(
        idPlantilla: String,
        idPaciente: String,
        diasAsignados: List<DiaSemana>
    ): Flow<Result<Routine>>

    // --- Invitaciones a pacientes ---

    fun inviteByEmail(email: String): Flow<Result<Invitation>>

    fun getInvitations(): Flow<Result<List<Invitation>>>

    fun cancelInvitation(idInvitacion: String): Flow<Result<Unit>>
}

data class RoutineExerciseInput(
    val idEjercicio: String,
    val repeticionesObjetivo: Int,
    val series: Int
)
