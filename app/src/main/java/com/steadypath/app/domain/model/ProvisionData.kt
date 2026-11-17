package com.steadypath.app.domain.model

/** Datos que necesita POST /api/usuarios/provision (el email sale del token). */
data class ProvisionData(
    val rol: String,
    val nombre: String,
    val edad: Int? = null,
    val idProfesionalAsignado: String? = null,
    val especialidad: String? = null,
    val tipoProfesional: String? = null,
    val matricula: String? = null,
    val institucion: String? = null
)
