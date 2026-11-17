package com.steadypath.app.domain.model

/**
 * Reporte agregado de progreso del paciente.
 * Espeja ProgresoPacienteResponse del backend.
 */
data class ProgressReport(
    val sesionesTotales: Int,
    val ejerciciosRealizados: Int,
    val minutosTotales: Double,
    val diasActivos: Int,
    val rachaDias: Int,
    val promedioEva: Double?,
    /** Últimos 7 días en orden cronológico; el último elemento es hoy. */
    val actividadSemanal: List<DailyActivity>,
    val progresoPorZona: List<ZoneProgress>,
    val historialReciente: List<RecentSession>
)

data class DailyActivity(
    /** Fecha local del día en ISO (yyyy-MM-dd...). */
    val fecha: String,
    val minutos: Double,
    val sesiones: Int
)

data class ZoneProgress(
    val zona: String,
    val porcentajeCumplimiento: Double
)

data class RecentSession(
    val idSesion: String,
    val nombreRutina: String?,
    val fechaHoraInicio: String,
    val duracionMinutos: Double,
    val estado: String,
    val resultados: List<SessionResult>
)
