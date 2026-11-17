package com.steadypath.app.data.dto

import com.google.gson.annotations.SerializedName

// Sin idProfesional: sale del token.
data class CreateInvitationRequestDto(
    @SerializedName("email") val email: String
)

data class InvitationDto(
    @SerializedName("idInvitacion") val idInvitacion: String,
    @SerializedName("email") val email: String,
    @SerializedName("estado") val estado: String,
    @SerializedName("fechaInvitacion") val fechaInvitacion: String,
    // Solo viene completo cuando la consulta es del lado del paciente invitado.
    @SerializedName("nombreProfesional") val nombreProfesional: String? = null
)
