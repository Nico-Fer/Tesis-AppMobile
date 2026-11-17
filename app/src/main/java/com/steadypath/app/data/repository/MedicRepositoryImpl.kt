package com.steadypath.app.data.repository

import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.dto.AssignTemplateRequestDto
import com.steadypath.app.data.dto.CreateInvitationRequestDto
import com.steadypath.app.data.dto.CreateRoutineExerciseDto
import com.steadypath.app.data.dto.CreateRoutineRequestDto
import com.steadypath.app.data.dto.CreateTemplateRequestDto
import com.steadypath.app.data.dto.EditTemplateRequestDto
import com.steadypath.app.data.dto.ToleranceRequestDto
import com.steadypath.app.data.mapper.toDomain
import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.model.PatientSummary
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.RoutineTemplate
import com.steadypath.app.domain.model.Tolerance
import com.steadypath.app.domain.repository.MedicRepository
import com.steadypath.app.domain.repository.RoutineExerciseInput
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class MedicRepositoryImpl @Inject constructor(
    private val apiService: RehabApiService,
    private val caller: SafeApiCaller
) : MedicRepository {

    override fun getMyPatients(): Flow<Result<List<PatientSummary>>> = caller.flowCall(
        call = { apiService.getMyPatients() },
        map = { dtos -> dtos.map { it.toDomain() } }
    )

    override fun removePatient(idPaciente: String): Flow<Result<Unit>> = caller.flowCall(
        call = { apiService.removePatient(idPaciente) },
        map = { }
    )

    override fun createRoutine(
        idPaciente: String,
        nombreRutina: String,
        diasAsignados: List<DiaSemana>,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<Routine>> = caller.flowCall(
        call = {
            apiService.createRoutine(
                CreateRoutineRequestDto(
                    idPaciente = idPaciente,
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

    override fun setTolerance(
        idPaciente: String,
        idEjercicio: String,
        anguloMinimo: Double,
        anguloMaximo: Double
    ): Flow<Result<Tolerance>> = caller.flowCall(
        call = {
            apiService.setTolerance(
                ToleranceRequestDto(idPaciente, idEjercicio, anguloMinimo, anguloMaximo)
            )
        },
        map = { it.toDomain() }
    )

    override fun createTemplate(
        nombreRutina: String,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<RoutineTemplate>> = caller.flowCall(
        call = { apiService.createTemplate(CreateTemplateRequestDto(nombreRutina, ejercicios.toDtos())) },
        map = { it.toDomain() }
    )

    override fun getTemplates(): Flow<Result<List<RoutineTemplate>>> = caller.flowCall(
        call = { apiService.getTemplates() },
        map = { dtos -> dtos.map { it.toDomain() } }
    )

    override fun getTemplate(idPlantilla: String): Flow<Result<RoutineTemplate>> = caller.flowCall(
        call = { apiService.getTemplate(idPlantilla) },
        map = { it.toDomain() }
    )

    override fun editTemplate(
        idPlantilla: String,
        nombreRutina: String,
        ejercicios: List<RoutineExerciseInput>
    ): Flow<Result<RoutineTemplate>> = caller.flowCall(
        call = { apiService.editTemplate(idPlantilla, EditTemplateRequestDto(nombreRutina, ejercicios.toDtos())) },
        map = { it.toDomain() }
    )

    override fun assignTemplate(
        idPlantilla: String,
        idPaciente: String,
        diasAsignados: List<DiaSemana>
    ): Flow<Result<Routine>> = caller.flowCall(
        call = {
            apiService.assignTemplate(
                idPlantilla,
                AssignTemplateRequestDto(idPaciente, diasAsignados.map { it.ordinal })
            )
        },
        map = { it.toDomain() }
    )

    override fun inviteByEmail(email: String): Flow<Result<Invitation>> = caller.flowCall(
        call = { apiService.createInvitation(CreateInvitationRequestDto(email)) },
        map = { it.toDomain() }
    )

    override fun getInvitations(): Flow<Result<List<Invitation>>> = caller.flowCall(
        call = { apiService.getInvitations() },
        map = { dtos -> dtos.map { it.toDomain() } }
    )

    override fun cancelInvitation(idInvitacion: String): Flow<Result<Unit>> = caller.flowCall(
        call = { apiService.cancelInvitation(idInvitacion) },
        map = { }
    )

    private fun List<RoutineExerciseInput>.toDtos(): List<CreateRoutineExerciseDto> = map {
        CreateRoutineExerciseDto(
            idEjercicio = it.idEjercicio,
            repeticionesObjetivo = it.repeticionesObjetivo,
            series = it.series
        )
    }
}
