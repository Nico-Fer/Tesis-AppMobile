package com.steadypath.app.data.dto

import com.google.gson.annotations.SerializedName

data class RoutineDto(
    @SerializedName("idRutina") val idRutina: String,
    @SerializedName("idPaciente") val idPaciente: String?,
    @SerializedName("idProfesionalAsignador") val idProfesionalAsignador: String?,
    @SerializedName("nombreRutina") val nombreRutina: String,
    @SerializedName("fechaCreacion") val fechaCreacion: String?,
    @SerializedName("activa") val activa: Boolean,
    @SerializedName("diasAsignados") val diasAsignados: List<Int>?,
    @SerializedName("ejercicios") val ejercicios: List<RoutineExerciseDto>?
)

data class RoutineExerciseDto(
    @SerializedName("idRutinaEjercicio") val idRutinaEjercicio: String,
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("nombreEjercicio") val nombreEjercicio: String?,
    @SerializedName("repeticionesObjetivo") val repeticionesObjetivo: Int,
    @SerializedName("series") val series: Int,
    @SerializedName("duracionMinutos") val duracionMinutos: Int = 0,
    // Enum ZonaCorporal serializado como número (0-18); mapeo completo en DtoMappers.ZONAS
    @SerializedName("zonasAfectadas") val zonasAfectadas: List<Int> = emptyList(),
    @SerializedName("nivelDificultad") val nivelDificultad: String? = null
)

// Sin idProfesional: el backend lo saca del token.
data class CreateRoutineRequestDto(
    @SerializedName("idPaciente") val idPaciente: String,
    @SerializedName("nombreRutina") val nombreRutina: String,
    @SerializedName("diasAsignados") val diasAsignados: List<Int>,
    @SerializedName("ejercicios") val ejercicios: List<CreateRoutineExerciseDto>
)

data class CreateRoutineExerciseDto(
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("repeticionesObjetivo") val repeticionesObjetivo: Int,
    @SerializedName("series") val series: Int
)

// POST /api/rutinas: el paciente es el del token.
// idCliente lo genera el teléfono una sola vez por rutina y el backend lo usa como idRutina:
// una rutina creada sin red ya nace con su identificador definitivo (las sesiones hechas con
// ella lo citan) y un reenvío devuelve la que ya se guardó en lugar de duplicarla.
data class CreateOwnRoutineRequestDto(
    @SerializedName("idCliente") val idCliente: String? = null,
    @SerializedName("nombreRutina") val nombreRutina: String,
    @SerializedName("diasAsignados") val diasAsignados: List<Int>,
    @SerializedName("ejercicios") val ejercicios: List<CreateRoutineExerciseDto>
)

data class RoutineStateRequestDto(
    @SerializedName("activa") val activa: Boolean
)

// PUT /api/rutinas/{id}/ejercicios: reemplaza por completo días y ejercicios.
data class EditRoutineRequestDto(
    @SerializedName("diasAsignados") val diasAsignados: List<Int>,
    @SerializedName("ejercicios") val ejercicios: List<CreateRoutineExerciseDto>
)

// Espeja CumplimientoRutinaResponse del backend (TesisApi.DTOs/SesionesDtos.cs)
data class RoutineComplianceDto(
    @SerializedName("idRutina") val idRutina: String,
    @SerializedName("nombreRutina") val nombreRutina: String,
    @SerializedName("activa") val activa: Boolean,
    @SerializedName("sesionesRegistradas") val sesionesRegistradas: Int,
    @SerializedName("repeticionesObjetivo") val repeticionesObjetivo: Int,
    @SerializedName("repeticionesCorrectas") val repeticionesCorrectas: Int,
    @SerializedName("porcentajeCumplimiento") val porcentajeCumplimiento: Double,
    @SerializedName("ejercicios") val ejercicios: List<ExerciseComplianceDto>?
)

data class ExerciseComplianceDto(
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("nombreEjercicio") val nombreEjercicio: String,
    @SerializedName("repeticionesObjetivo") val repeticionesObjetivo: Int,
    @SerializedName("repeticionesCorrectas") val repeticionesCorrectas: Int,
    @SerializedName("porcentajeCumplimiento") val porcentajeCumplimiento: Double
)

data class PatientSummaryDto(
    @SerializedName("idPaciente") val idPaciente: String,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("email") val email: String,
    @SerializedName("edad") val edad: Int?,
    @SerializedName("cantidadRutinasActivas") val cantidadRutinasActivas: Int,
    @SerializedName("fechaUltimaSesion") val fechaUltimaSesion: String?,
    // Revocó el consentimiento: el backend manda rutinas y última sesión vacías.
    @SerializedName("consentimientoRevocado") val consentimientoRevocado: Boolean = false
)

// Sin idProfesional: el backend lo saca del token.
data class ToleranceRequestDto(
    @SerializedName("idPaciente") val idPaciente: String,
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("anguloMinimo") val anguloMinimo: Double,
    @SerializedName("anguloMaximo") val anguloMaximo: Double
)

data class ToleranceDto(
    @SerializedName("idTolerancia") val idTolerancia: String,
    @SerializedName("idPaciente") val idPaciente: String?,
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("anguloMinimo") val anguloMinimo: Double,
    @SerializedName("anguloMaximo") val anguloMaximo: Double
)

data class HealthDto(
    @SerializedName("status") val status: String,
    @SerializedName("version") val version: String?,
    @SerializedName("utc") val utc: String?
)
