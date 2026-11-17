package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.RoutineCompliance
import kotlinx.coroutines.flow.Flow

/** Accesible por el paciente o su profesional; otro usuario recibe 403. */
interface RoutineRepository {

    fun getPatientRoutines(
        idPaciente: String,
        soloActivas: Boolean = true
    ): Flow<Result<List<Routine>>>

    fun getRoutine(idRutina: String): Flow<Result<Routine>>

    /** Crea una rutina propia del paciente autenticado (el id sale del token). */
    fun createOwnRoutine(
        nombreRutina: String,
        diasAsignados: List<DiaSemana>,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<Routine>>

    fun getRoutineCompliance(idRutina: String): Flow<Result<RoutineCompliance>>

    fun getPatientRoutineCompliance(
        idPaciente: String,
        soloActivas: Boolean = true
    ): Flow<Result<List<RoutineCompliance>>>

    /** Solo el profesional a cargo; un paciente recibe 403. Devuelve la rutina actualizada. */
    fun setRoutineActive(idRutina: String, activa: Boolean): Flow<Result<Routine>>

    /** Reemplaza por completo días y ejercicios. Solo el profesional a cargo. */
    fun editRoutineExercises(
        idRutina: String,
        diasAsignados: List<DiaSemana>,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<Routine>>
}
