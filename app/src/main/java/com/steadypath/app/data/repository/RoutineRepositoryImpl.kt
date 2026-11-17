package com.steadypath.app.data.repository

import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.dto.CreateOwnRoutineRequestDto
import com.steadypath.app.data.dto.CreateRoutineExerciseDto
import com.steadypath.app.data.dto.EditRoutineRequestDto
import com.steadypath.app.data.dto.RoutineStateRequestDto
import com.steadypath.app.data.mapper.toDomain
import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.RoutineCompliance
import com.steadypath.app.domain.repository.RoutineExerciseInput
import com.steadypath.app.domain.repository.RoutineRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RoutineRepositoryImpl @Inject constructor(
    private val apiService: RehabApiService,
    private val caller: SafeApiCaller
) : RoutineRepository {

    override fun getPatientRoutines(
        idPaciente: String,
        soloActivas: Boolean
    ): Flow<Result<List<Routine>>> = caller.flowCall(
        call = { apiService.getPatientRoutines(idPaciente, soloActivas) },
        map = { dtos -> dtos.map { it.toDomain() } }
    )

    override fun getRoutine(idRutina: String): Flow<Result<Routine>> = caller.flowCall(
        call = { apiService.getRoutine(idRutina) },
        map = { it.toDomain() }
    )

    override fun createOwnRoutine(
        nombreRutina: String,
        diasAsignados: List<DiaSemana>,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<Routine>> = caller.flowCall(
        call = {
            apiService.createOwnRoutine(
                CreateOwnRoutineRequestDto(
                    nombreRutina = nombreRutina,
                    diasAsignados = diasAsignados.map { it.ordinal },
                    ejercicios = ejercicios.map {
                        CreateRoutineExerciseDto(
                            idEjercicio = it.idEjercicio,
                            repeticionesObjetivo = it.repeticionesObjetivo,
                            series = it.series
                        )
                    }
                )
            )
        },
        map = { it.toDomain() }
    )

    override fun getRoutineCompliance(idRutina: String): Flow<Result<RoutineCompliance>> =
        caller.flowCall(
            call = { apiService.getRoutineCompliance(idRutina) },
            map = { it.toDomain() }
        )

    override fun getPatientRoutineCompliance(
        idPaciente: String,
        soloActivas: Boolean
    ): Flow<Result<List<RoutineCompliance>>> = caller.flowCall(
        call = { apiService.getPatientRoutineCompliance(idPaciente, soloActivas) },
        map = { dtos -> dtos.map { it.toDomain() } }
    )

    override fun setRoutineActive(idRutina: String, activa: Boolean): Flow<Result<Routine>> =
        caller.flowCall(
            call = { apiService.setRoutineState(idRutina, RoutineStateRequestDto(activa)) },
            map = { it.toDomain() }
        )

    override fun editRoutineExercises(
        idRutina: String,
        diasAsignados: List<DiaSemana>,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<Routine>> = caller.flowCall(
        call = {
            apiService.editRoutineExercises(
                idRutina,
                EditRoutineRequestDto(
                    diasAsignados = diasAsignados.map { it.ordinal },
                    ejercicios = ejercicios.map {
                        CreateRoutineExerciseDto(
                            idEjercicio = it.idEjercicio,
                            repeticionesObjetivo = it.repeticionesObjetivo,
                            series = it.series
                        )
                    }
                )
            )
        },
        map = { it.toDomain() }
    )
}
