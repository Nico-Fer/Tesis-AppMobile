package com.steadypath.app.domain.model

// Espeja el enum DiaSemana del backend: viaja por número (0=Lunes..6=Domingo).
enum class DiaSemana(val etiqueta: String, val nombre: String) {
    LUNES("L", "Lunes"),
    MARTES("M", "Martes"),
    MIERCOLES("X", "Miércoles"),
    JUEVES("J", "Jueves"),
    VIERNES("V", "Viernes"),
    SABADO("S", "Sábado"),
    DOMINGO("D", "Domingo")
}

data class Routine(
    val idRutina: String,
    val idPaciente: String,
    /** null = la creo el propio paciente. */
    val idProfesionalAsignador: String?,
    val nombreRutina: String,
    val fechaCreacion: String,
    val activa: Boolean,
    /** Vacío = rutina flexible sin días fijos. */
    val diasAsignados: List<DiaSemana>,
    val ejercicios: List<RoutineExercise>
)

data class RoutineExercise(
    val idRutinaEjercicio: String,
    val idEjercicio: String,
    val nombreEjercicio: String,
    val repeticionesObjetivo: Int,
    val series: Int,
    val duracionMinutos: Int = 0,
    val zonasAfectadas: List<String> = emptyList(),
    val nivelDificultad: String = ""
)
