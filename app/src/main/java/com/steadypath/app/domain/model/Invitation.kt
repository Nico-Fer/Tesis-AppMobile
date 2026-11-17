package com.steadypath.app.domain.model

/** Invitación pendiente de un profesional a un paciente por email. */
data class Invitation(
    val idInvitacion: String,
    val email: String,
    val estado: String,
    val fechaInvitacion: String,
    // Solo viene completo cuando la consulta es del lado del paciente invitado.
    val nombreProfesional: String? = null
)
