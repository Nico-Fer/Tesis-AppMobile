package com.steadypath.app.data.dto

import com.google.gson.annotations.SerializedName

// Sin idProfesional: el dueño es el profesional del token.
data class CreateTemplateRequestDto(
    @SerializedName("nombreRutina") val nombreRutina: String,
    @SerializedName("ejercicios") val ejercicios: List<CreateRoutineExerciseDto>
)

data class EditTemplateRequestDto(
    @SerializedName("nombreRutina") val nombreRutina: String,
    @SerializedName("ejercicios") val ejercicios: List<CreateRoutineExerciseDto>
)

data class AssignTemplateRequestDto(
    @SerializedName("idPaciente") val idPaciente: String,
    @SerializedName("diasAsignados") val diasAsignados: List<Int>
)

data class TemplateDto(
    @SerializedName("idPlantilla") val idPlantilla: String,
    @SerializedName("nombreRutina") val nombreRutina: String,
    @SerializedName("fechaCreacion") val fechaCreacion: String?,
    @SerializedName("cantidadPacientesAsignados") val cantidadPacientesAsignados: Int,
    @SerializedName("ejercicios") val ejercicios: List<TemplateExerciseDto>?
)

data class TemplateExerciseDto(
    @SerializedName("idPlantillaRutinaEjercicio") val idPlantillaRutinaEjercicio: String,
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("nombreEjercicio") val nombreEjercicio: String,
    @SerializedName("repeticionesObjetivo") val repeticionesObjetivo: Int,
    @SerializedName("series") val series: Int
)
