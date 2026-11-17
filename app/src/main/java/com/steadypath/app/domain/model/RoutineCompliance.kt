package com.steadypath.app.domain.model

/** Adherencia al plan: repeticiones correctas sobre las prescritas por la rutina. */
data class RoutineCompliance(
    val idRutina: String,
    val nombreRutina: String,
    val activa: Boolean,
    val sesionesRegistradas: Int,
    val repeticionesObjetivo: Int,
    val repeticionesCorrectas: Int,
    val porcentajeCumplimiento: Double,
    val ejercicios: List<ExerciseCompliance>
)

data class ExerciseCompliance(
    val idEjercicio: String,
    val nombreEjercicio: String,
    val repeticionesObjetivo: Int,
    val repeticionesCorrectas: Int,
    val porcentajeCumplimiento: Double
)
