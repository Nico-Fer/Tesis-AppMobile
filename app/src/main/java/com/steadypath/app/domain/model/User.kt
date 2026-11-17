package com.steadypath.app.domain.model

data class User(
    val idUsuario: String,
    val nombre: String,
    val email: String,
    val rol: String,
    val fechaRegistro: String,
    // Solo presentes cuando rol es Profesional.
    val tipoProfesional: String? = null,
    val matricula: String? = null,
    val institucion: String? = null,
    // Solo presentes cuando rol es Paciente.
    val telefono: String? = null,
    val edad: Int? = null,
    // Revocó el consentimiento (RF-48): datos bloqueados, no borrados.
    val consentimientoRevocado: Boolean = false
)
