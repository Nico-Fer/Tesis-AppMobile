package com.steadypath.app.domain.model

/** Ángulos tolerados de un ejercicio para un paciente. */
data class Tolerance(
    val idTolerancia: String,
    val idPaciente: String?,
    val idEjercicio: String,
    val anguloMinimo: Double,
    val anguloMaximo: Double
)
