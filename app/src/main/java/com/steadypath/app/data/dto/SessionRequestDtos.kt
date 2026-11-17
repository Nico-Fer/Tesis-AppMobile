package com.steadypath.app.data.dto

import com.google.gson.annotations.SerializedName

// Sin idPaciente: el backend registra la sesión a nombre del usuario del token.
// idCliente lo genera el teléfono una sola vez por sesión: si un reenvío llega dos veces,
// el backend devuelve la que ya guardó en lugar de duplicarla.
data class CreateSessionRequestDto(
    @SerializedName("idCliente") val idCliente: String? = null,
    @SerializedName("idRutina") val idRutina: String? = null,
    @SerializedName("fechaHoraInicio") val fechaHoraInicio: String? = null,
    @SerializedName("duracionMinutos") val duracionMinutos: Double,
    @SerializedName("estado") val estado: String,
    @SerializedName("resultados") val resultados: List<SessionResultRequestDto>
)

data class SessionResultRequestDto(
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("repeticionesRealizadas") val repeticionesRealizadas: Int,
    @SerializedName("repeticionesCorrectas") val repeticionesCorrectas: Int,
    @SerializedName("porcentajeCorrectitud") val porcentajeCorrectitud: Double,
    @SerializedName("escalaEVA") val escalaEVA: Int?
)
