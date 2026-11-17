package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.Compliance
import com.steadypath.app.domain.model.PainRecord
import com.steadypath.app.domain.model.ProgressReport
import com.steadypath.app.domain.model.Session
import kotlinx.coroutines.flow.Flow

interface SessionRepository {

    // Sin idPaciente: el backend la registra a nombre del usuario del token.
    fun saveSession(
        idRutina: String?,
        fechaHoraInicio: String?,
        duracionMinutos: Double,
        estado: String,
        resultados: List<SessionResultInput>
    ): Flow<Result<Session>>

    fun getHistory(idPaciente: String): Flow<Result<List<Session>>>

    fun getEvaHistory(idPaciente: String): Flow<Result<List<PainRecord>>>

    fun getCompliance(idPaciente: String): Flow<Result<Compliance>>

    fun getProgressReport(idPaciente: String, tzOffsetMinutes: Int): Flow<Result<ProgressReport>>
}

data class SessionResultInput(
    val idEjercicio: String,
    val repeticionesRealizadas: Int,
    val repeticionesCorrectas: Int,
    val porcentajeCorrectitud: Double,
    val escalaEVA: Int?
)
