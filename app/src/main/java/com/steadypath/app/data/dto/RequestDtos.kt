package com.steadypath.app.data.dto

import com.google.gson.annotations.SerializedName

/**
 * Body de POST /api/usuarios/provision. Email y contraseña no viajan:
 * el backend saca el email del ID token de Cognito.
 */
data class ProvisionRequestDto(
    @SerializedName("rol") val rol: String,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("edad") val edad: Int? = null,
    @SerializedName("idProfesionalAsignado") val idProfesionalAsignado: String? = null,
    @SerializedName("especialidad") val especialidad: String? = null,
    @SerializedName("tipoProfesional") val tipoProfesional: String? = null,
    @SerializedName("matricula") val matricula: String? = null,
    @SerializedName("institucion") val institucion: String? = null
)

/** Body de PUT /api/usuarios/perfil-profesional. El email no viaja: no se puede cambiar. */
data class UpdateProfessionalProfileRequestDto(
    @SerializedName("nombre") val nombre: String,
    @SerializedName("tipoProfesional") val tipoProfesional: String?,
    @SerializedName("matricula") val matricula: String?,
    @SerializedName("institucion") val institucion: String?
)

/** Body de PUT /api/usuarios/perfil-paciente. El email no viaja: no se puede cambiar. */
data class UpdatePatientProfileRequestDto(
    @SerializedName("nombre") val nombre: String,
    @SerializedName("telefono") val telefono: String?
)

/** Body de PUT /api/pacientes/tolerancias. Sin idPaciente: sale del token. */
data class CalibrateOwnToleranceRequestDto(
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("anguloMinimo") val anguloMinimo: Double,
    @SerializedName("anguloMaximo") val anguloMaximo: Double
)

/** Body de PUT /api/pacientes/referencias. Sin idPaciente: sale del token. */
data class SaveOwnReferenceRequestDto(
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("referenciaJson") val referenciaJson: String
)

/** Body de POST /api/medicos/referencias: el profesional graba para un paciente a cargo. */
data class SavePatientReferenceRequestDto(
    @SerializedName("idPaciente") val idPaciente: String,
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("referenciaJson") val referenciaJson: String
)
