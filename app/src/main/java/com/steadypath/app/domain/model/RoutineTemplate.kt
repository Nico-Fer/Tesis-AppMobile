package com.steadypath.app.domain.model

/** Rutina reutilizable del profesional; se asigna a un paciente eligiendo días recién ahí. */
data class RoutineTemplate(
    val idPlantilla: String,
    val nombreRutina: String,
    val fechaCreacion: String,
    val cantidadPacientesAsignados: Int,
    val ejercicios: List<TemplateExercise>
)

data class TemplateExercise(
    val idPlantillaRutinaEjercicio: String,
    val idEjercicio: String,
    val nombreEjercicio: String,
    val repeticionesObjetivo: Int,
    val series: Int
)
