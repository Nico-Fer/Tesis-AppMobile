package com.steadypath.app.data.dto

import com.google.gson.annotations.SerializedName

data class UserResponseDto(
    @SerializedName("idUsuario") val idUsuario: String,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("email") val email: String,
    @SerializedName("rol") val rol: String,
    @SerializedName("fechaRegistro") val fechaRegistro: String,
    // Solo presentes cuando rol es Profesional.
    @SerializedName("tipoProfesional") val tipoProfesional: String?,
    @SerializedName("matricula") val matricula: String?,
    @SerializedName("institucion") val institucion: String?,
    // Solo presentes cuando rol es Paciente.
    @SerializedName("telefono") val telefono: String? = null,
    @SerializedName("edad") val edad: Int? = null,
    // Solo paciente: cuándo revocó el consentimiento; null si está vigente.
    @SerializedName("consentimientoRevocadoEn") val consentimientoRevocadoEn: String? = null
)

// Espeja ProfesionalAsignadoResponse: siempre 200 con body, aunque no haya profesional asignado.
data class AssignedProfessionalDto(
    @SerializedName("tieneProfesionalAsignado") val tieneProfesionalAsignado: Boolean,
    @SerializedName("idProfesional") val idProfesional: String?,
    @SerializedName("nombre") val nombre: String?,
    @SerializedName("tipoProfesional") val tipoProfesional: String?
)

// Espeja ToleranciaPropiaResponse: siempre 200 con body, aunque el paciente no haya calibrado
// todavía (tieneCalibracion=false y los ángulos vienen null).
data class OwnToleranceDto(
    @SerializedName("tieneCalibracion") val tieneCalibracion: Boolean,
    @SerializedName("anguloMinimo") val anguloMinimo: Double?,
    @SerializedName("anguloMaximo") val anguloMaximo: Double?
)

data class ExerciseDto(
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("nombre") val nombre: String,
    @SerializedName("descripcion") val descripcion: String,
    @SerializedName("urlVideoExplicativo") val urlVideoExplicativo: String?,
    // Enum ZonaCorporal serializado como número (0-18); mapeo completo en DtoMappers.ZONAS
    @SerializedName("zonasAfectadas") val zonasAfectadas: List<Int>,
    @SerializedName("nivelDificultad") val nivelDificultad: String,
    @SerializedName("duracionMinutos") val duracionMinutos: Int,
    // Slug de MovementPatternCatalog; null = no se puede grabar referencia para este ejercicio.
    @SerializedName("patronMovimiento") val patronMovimiento: String? = null
)

// Espeja ReferenciaMovimientoResponse: siempre 200 con body, tieneReferencia=false = sin
// referencia remota (el cliente cae a la local).
data class MovementReferenceDto(
    @SerializedName("tieneReferencia") val tieneReferencia: Boolean,
    @SerializedName("idEjercicio") val idEjercicio: String?,
    @SerializedName("referenciaJson") val referenciaJson: String?,
    @SerializedName("fechaActualizacion") val fechaActualizacion: String?,
    @SerializedName("creadaPorProfesional") val creadaPorProfesional: Boolean?
)

// Espeja VideoEjercicioResponse: URL de reproducción ya firmada (S3, vence a los 15 min)
data class ExerciseVideoDto(
    @SerializedName("nombreArchivo") val nombreArchivo: String,
    @SerializedName("url") val url: String,
    @SerializedName("expiraUtc") val expiraUtc: String
)

data class SessionDto(
    @SerializedName("idSesion") val idSesion: String,
    @SerializedName("idPaciente") val idPaciente: String?,
    // Presentes en HistorialSesionResponse; null = sesión de práctica libre.
    @SerializedName("idRutina") val idRutina: String?,
    @SerializedName("nombreRutina") val nombreRutina: String?,
    @SerializedName("fechaHoraInicio") val fechaHoraInicio: String,
    @SerializedName("duracionMinutos") val duracionMinutos: Double,
    @SerializedName("estado") val estado: String,
    @SerializedName("resultados") val resultados: List<SessionResultDto>?
)

data class SessionResultDto(
    @SerializedName("idResultado") val idResultado: String,
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("nombreEjercicio") val nombreEjercicio: String?,
    @SerializedName("repeticionesRealizadas") val repeticionesRealizadas: Int,
    @SerializedName("repeticionesCorrectas") val repeticionesCorrectas: Int,
    @SerializedName("porcentajeCorrectitud") val porcentajeCorrectitud: Double,
    @SerializedName("escalaEVA") val escalaEVA: Int?
)

data class EvaRecordDto(
    @SerializedName("idSesion") val idSesion: String,
    @SerializedName("fecha") val fecha: String,
    @SerializedName("idEjercicio") val idEjercicio: String,
    @SerializedName("nombreEjercicio") val nombreEjercicio: String,
    @SerializedName("escalaEVA") val escalaEVA: Int
)

data class ComplianceDto(
    @SerializedName("idPaciente") val idPaciente: String,
    @SerializedName("porcentajeCumplimiento") val porcentajeCumplimiento: Double
)

// Espeja ProgresoPacienteResponse: GET api/sesiones/progreso/{idPaciente}
data class ProgressReportDto(
    @SerializedName("idPaciente") val idPaciente: String,
    @SerializedName("sesionesTotales") val sesionesTotales: Int,
    @SerializedName("ejerciciosRealizados") val ejerciciosRealizados: Int,
    @SerializedName("minutosTotales") val minutosTotales: Double,
    @SerializedName("diasActivos") val diasActivos: Int,
    @SerializedName("rachaDias") val rachaDias: Int,
    @SerializedName("promedioEVA") val promedioEVA: Double?,
    @SerializedName("actividadSemanal") val actividadSemanal: List<DailyActivityDto>?,
    @SerializedName("progresoPorZona") val progresoPorZona: List<ZoneProgressDto>?,
    @SerializedName("historialReciente") val historialReciente: List<RecentSessionDto>?
)

data class DailyActivityDto(
    @SerializedName("fecha") val fecha: String,
    @SerializedName("minutos") val minutos: Double,
    @SerializedName("sesiones") val sesiones: Int
)

data class ZoneProgressDto(
    @SerializedName("zona") val zona: String,
    @SerializedName("porcentajeCumplimiento") val porcentajeCumplimiento: Double
)

// Espeja HistorialSesionResponse: a diferencia de SessionDto trae nombreRutina
data class RecentSessionDto(
    @SerializedName("idSesion") val idSesion: String,
    @SerializedName("nombreRutina") val nombreRutina: String?,
    @SerializedName("fechaHoraInicio") val fechaHoraInicio: String,
    @SerializedName("duracionMinutos") val duracionMinutos: Double,
    @SerializedName("estado") val estado: String,
    @SerializedName("resultados") val resultados: List<SessionResultDto>?
)

data class ErrorResponseDto(
    @SerializedName("message") val message: String
)

/** Resumen de lo que guarda el backend sobre el usuario (GET api/usuarios/mis-datos). */
data class MisDatosDto(
    @SerializedName("perfil") val perfil: UserResponseDto,
    @SerializedName("ultimaActividad") val ultimaActividad: String,
    @SerializedName("bajaPorInactividadDesde") val bajaPorInactividadDesde: String,
    @SerializedName("profesionalAsignado") val profesionalAsignado: String? = null,
    @SerializedName("sesiones") val sesiones: Int = 0,
    @SerializedName("registrosDeDolor") val registrosDeDolor: Int = 0,
    @SerializedName("rutinas") val rutinas: Int = 0,
    @SerializedName("tolerancias") val tolerancias: Int = 0,
    @SerializedName("referenciasDeMovimiento") val referenciasDeMovimiento: Int = 0,
    @SerializedName("pacientesACargo") val pacientesACargo: Int = 0,
    @SerializedName("plantillasDeRutina") val plantillasDeRutina: Int = 0
)
