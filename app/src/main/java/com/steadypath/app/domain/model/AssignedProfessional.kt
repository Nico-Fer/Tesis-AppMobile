package com.steadypath.app.domain.model

/** El profesional asignado a un paciente, si tiene uno. */
data class AssignedProfessional(
    val tieneProfesionalAsignado: Boolean,
    val idProfesional: String?,
    val nombre: String?,
    val tipoProfesional: String?
)
