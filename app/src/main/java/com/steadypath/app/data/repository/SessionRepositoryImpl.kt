package com.steadypath.app.data.repository

import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.dto.CreateSessionRequestDto
import com.steadypath.app.data.dto.SessionResultRequestDto
import com.steadypath.app.data.mapper.toDomain
import com.steadypath.app.domain.model.Compliance
import com.steadypath.app.domain.model.PainRecord
import com.steadypath.app.domain.model.ProgressReport
import com.steadypath.app.domain.model.Session
import com.steadypath.app.domain.repository.SessionRepository
import com.steadypath.app.domain.repository.SessionResultInput
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class SessionRepositoryImpl @Inject constructor(
    private val apiService: RehabApiService,
    private val caller: SafeApiCaller
) : SessionRepository {

    override fun saveSession(
        idRutina: String?,
        fechaHoraInicio: String?,
        duracionMinutos: Double,
        estado: String,
        resultados: List<SessionResultInput>
    ): Flow<Result<Session>> = caller.flowCall(
        call = {
            apiService.saveSession(
                CreateSessionRequestDto(
                    idRutina = idRutina,
                    fechaHoraInicio = fechaHoraInicio,
                    duracionMinutos = duracionMinutos,
                    estado = estado,
                    resultados = resultados.map {
                        SessionResultRequestDto(
                            idEjercicio = it.idEjercicio,
                            repeticionesRealizadas = it.repeticionesRealizadas,
                            repeticionesCorrectas = it.repeticionesCorrectas,
                            porcentajeCorrectitud = it.porcentajeCorrectitud,
                            escalaEVA = it.escalaEVA
                        )
                    }
                )
            )
        },
        map = { it.toDomain() }
    )

    override fun getHistory(idPaciente: String): Flow<Result<List<Session>>> = caller.flowCall(
        call = { apiService.getSessionHistory(idPaciente) },
        map = { dtos -> dtos.map { it.toDomain() } }
    )

    override fun getEvaHistory(idPaciente: String): Flow<Result<List<PainRecord>>> =
        caller.flowCall(
            call = { apiService.getEvaHistory(idPaciente) },
            map = { dtos -> dtos.map { it.toDomain() } }
        )

    override fun getCompliance(idPaciente: String): Flow<Result<Compliance>> = caller.flowCall(
        call = { apiService.getCompliance(idPaciente) },
        map = { it.toDomain() }
    )

    override fun getProgressReport(
        idPaciente: String,
        tzOffsetMinutes: Int
    ): Flow<Result<ProgressReport>> = caller.flowCall(
        call = { apiService.getProgressReport(idPaciente, tzOffsetMinutes) },
        map = { it.toDomain() }
    )
}
