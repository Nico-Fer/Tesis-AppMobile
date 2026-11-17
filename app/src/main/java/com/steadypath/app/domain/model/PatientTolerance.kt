package com.steadypath.app.domain.model

/**
 * La tolerancia angular que el propio paciente calibró para un ejercicio.
 * [tieneCalibracion] falso significa que todavía no calibró: los ángulos vienen `null` y no
 * hay que asumir un rango, hay que ofrecer calibrar.
 */
data class PatientTolerance(
    val tieneCalibracion: Boolean,
    val anguloMinimo: Double?,
    val anguloMaximo: Double?
)
