package com.steadypath.app.domain.model

/** Paciente en la lista del profesional (GET /api/medicos/pacientes). */
data class PatientSummary(
    val idPaciente: String,
    val nombre: String,
    val email: String,
    val edad: Int?,
    val cantidadRutinasActivas: Int,
    val fechaUltimaSesion: String?
)
