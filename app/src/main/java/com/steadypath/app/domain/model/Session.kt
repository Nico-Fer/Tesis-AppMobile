package com.steadypath.app.domain.model

data class Session(
    val idSesion: String,
    val idPaciente: String,
    /** null = sesión de práctica libre, sin rutina asociada. */
    val idRutina: String?,
    val nombreRutina: String?,
    val fechaHoraInicio: String,
    val duracionMinutos: Double,
    val estado: String,
    val resultados: List<SessionResult>
)

data class SessionResult(
    val idResultado: String,
    val idEjercicio: String,
    val nombreEjercicio: String?,
    val repeticionesRealizadas: Int,
    val repeticionesCorrectas: Int,
    val porcentajeCorrectitud: Double,
    val escalaEVA: Int?
)
