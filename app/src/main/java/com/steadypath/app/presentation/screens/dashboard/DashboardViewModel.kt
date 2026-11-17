package com.steadypath.app.presentation.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.steadypath.app.domain.model.Compliance
import com.steadypath.app.domain.model.DailyActivity
import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.domain.model.ProgressReport
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.domain.model.Session
import com.steadypath.app.domain.repository.UserSessionRepository
import com.steadypath.app.domain.usecase.AcceptInvitationUseCase
import com.steadypath.app.domain.usecase.GetComplianceUseCase
import com.steadypath.app.domain.usecase.GetMyInvitationsUseCase
import com.steadypath.app.domain.usecase.GetPatientRoutinesUseCase
import com.steadypath.app.domain.usecase.GetProgressReportUseCase
import com.steadypath.app.domain.usecase.GetSessionHistoryUseCase
import com.steadypath.app.domain.usecase.RejectInvitationUseCase
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.screens.routines.resumenDias
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

private val WEEK_DAY_LABELS = listOf("L", "M", "X", "J", "V", "S", "D")
private val WEEK_DAY_NAMES = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

// Hitos de racha de días seguidos. El emoji marca los hitos medianos en adelante (7+); el
// primero (3 días) queda en texto solo, para que la escalada del tono se note con el tiempo.
private val RACHA_HITOS = mapOf(
    3 to "¡Van 3 días seguidos! Arrancaste con todo.",
    7 to "¡Una semana completa sin faltar! Eso es constancia real. 👏",
    14 to "¡Dos semanas seguidas! Ya es un hábito. 👏",
    30 to "¡Un mes entero de rutina! Esto ya forma parte de tu día a día. 🎉",
    60 to "¡Dos meses seguidos, impresionante! 🎉",
    90 to "¡Tres meses sin faltar! Muy pocos llegan hasta acá. 🎉"
)

// Hitos de sesiones totales: no se resetean con una falta, complementan la racha.
private val SESIONES_HITOS = mapOf(
    10 to "¡Ya llevás 10 sesiones hechas! Cada una suma. 👏",
    25 to "25 sesiones completadas. Se nota el esfuerzo.",
    50 to "50 sesiones. Ya sos un habitué de tu rutina. 🎉",
    100 to "100 sesiones. Un número redondo para algo que hiciste vos, con constancia. 🎉"
)

private const val MENSAJE_VUELTA = "Qué bueno tenerte de vuelta. Lo que importa es seguir, no lo que quedó atrás."

private val MENSAJES_GENERICOS = listOf(
    "Cada paso cuenta, sigue así.",
    "Tu constancia es lo que hace la diferencia.",
    "Un día más, un paso más cerca.",
    "Lo importante es seguir apareciendo."
)

data class DailyComplianceBar(
    val dayLabel: String,
    val dayName: String,
    val heightFraction: Float,
    val isActive: Boolean
)

/** Rutina que corresponde hacer hoy según sus días asignados. */
data class TodayRoutine(
    val idRutina: String,
    val nombre: String,
    val diasLabel: String?,
    val completadaHoy: Boolean
)

data class DashboardData(
    val consentimientoRevocado: Boolean = false,
    val compliance: Compliance? = null,
    val todayRoutines: List<TodayRoutine> = emptyList(),
    val motivationalQuote: String = "Cada paso cuenta, sigue así.",
    val weeklyBars: List<DailyComplianceBar> = emptyList(),
    val pendingInvitations: List<Invitation> = emptyList()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val getComplianceUseCase: GetComplianceUseCase,
    private val getPatientRoutinesUseCase: GetPatientRoutinesUseCase,
    private val getSessionHistoryUseCase: GetSessionHistoryUseCase,
    private val getProgressReportUseCase: GetProgressReportUseCase,
    private val getMyInvitationsUseCase: GetMyInvitationsUseCase,
    private val acceptInvitationUseCase: AcceptInvitationUseCase,
    private val rejectInvitationUseCase: RejectInvitationUseCase,
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<DashboardData>>(UiState.Loading)
    val uiState: StateFlow<UiState<DashboardData>> = _uiState.asStateFlow()

    private val _invitationActionError = MutableStateFlow<String?>(null)
    val invitationActionError: StateFlow<String?> = _invitationActionError.asStateFlow()

    private val _patientName = MutableStateFlow("")
    val patientName: StateFlow<String> = _patientName.asStateFlow()

    // Sin loadDashboard() en init: la pantalla recarga en cada entrada (patrón RoutineList)
    // para reflejar una sesión recién completada.

    fun acceptInvitation(idInvitacion: String) {
        viewModelScope.launch {
            acceptInvitationUseCase(idInvitacion).fold(
                onSuccess = { loadDashboard() },
                onFailure = { error -> _invitationActionError.value = error.message ?: "Error al aceptar la invitación" }
            )
        }
    }

    fun rejectInvitation(idInvitacion: String) {
        viewModelScope.launch {
            rejectInvitationUseCase(idInvitacion).fold(
                onSuccess = { loadDashboard() },
                onFailure = { error -> _invitationActionError.value = error.message ?: "Error al rechazar la invitación" }
            )
        }
    }

    private var cargaEnCurso: Job? = null

    /**
     * Cada lectura emite primero lo guardado en el teléfono y después lo del servidor, así
     * que el inicio se arma con [combine] y se vuelve a publicar en cada emisión: sin red
     * (o con red lenta) la persona ve enseguida sus rutinas de hoy en vez de una pantalla
     * vacía. Antes se recolectaban en serie y cualquier fallo dejaba los valores por defecto.
     */
    fun loadDashboard() {
        cargaEnCurso?.cancel()
        cargaEnCurso = viewModelScope.launch {
            // Con datos ya en pantalla no se vuelve al spinner: se actualizan en el lugar.
            if (_uiState.value !is UiState.Success) _uiState.value = UiState.Loading

            val usuario = userSessionRepository.get()
            // Se relee en cada entrada, no solo al crear el ViewModel: Dashboard es el
            // destino de arranque del grafo y no se recrea al volver de "Editar perfil",
            // así que un nombre cargado una sola vez quedaba viejo tras editarlo.
            _patientName.value = usuario?.nombre.orEmpty()
            val patientId = usuario?.idUsuario

            val base = DashboardData(
                weeklyBars = buildEmptyWeekBars(),
                consentimientoRevocado = usuario?.consentimientoRevocado == true
            )
            if (patientId == null) {
                _uiState.value = UiState.Success(base)
                return@launch
            }

            // Las invitaciones no tienen copia local (hay que responderlas con red): llegan
            // aparte para no demorar el resto.
            val invitaciones = MutableStateFlow<List<Invitation>>(emptyList())
            launch { getMyInvitationsUseCase().onSuccess { invitaciones.value = it } }

            combine(
                getPatientRoutinesUseCase(patientId),
                getSessionHistoryUseCase(patientId),
                getComplianceUseCase(patientId),
                getProgressReportUseCase(patientId),
                invitaciones
            ) { rutinas, historial, cumplimiento, progreso, pendientes ->
                armar(
                    base = base,
                    rutinas = rutinas.getOrNull().orEmpty(),
                    historial = historial.getOrNull().orEmpty(),
                    cumplimiento = cumplimiento.getOrNull(),
                    progreso = progreso.getOrNull(),
                    invitaciones = pendientes
                )
            }.collect { _uiState.value = UiState.Success(it) }
        }
    }

    /** Lo que falló y no tenía copia llega vacío o `null` y deja su parte en el valor por defecto. */
    private fun armar(
        base: DashboardData,
        rutinas: List<Routine>,
        historial: List<Session>,
        cumplimiento: Compliance?,
        progreso: ProgressReport?,
        invitaciones: List<Invitation>
    ): DashboardData {
        val hoy = DiaSemana.entries[LocalDate.now().dayOfWeek.value - 1]
        val completadasHoy = completedTodayRoutineIds(historial)
        val actividad = actividadConSesionesLocales(
            delServidor = progreso?.actividadSemanal.orEmpty(),
            historial = historial,
            fechaLocal = ::localDateOf
        )
        return base.copy(
            compliance = cumplimiento,
            pendingInvitations = invitaciones,
            todayRoutines = rutinas
                .filter { it.activa && hoy in it.diasAsignados }
                .map { rutina ->
                    TodayRoutine(
                        idRutina = rutina.idRutina,
                        nombre = rutina.nombreRutina,
                        diasLabel = rutina.diasAsignados.resumenDias(),
                        completadaHoy = rutina.idRutina in completadasHoy
                    )
                },
            motivationalQuote = progreso
                ?.let { motivationalMessageFor(it.rachaDias, it.sesionesTotales) }
                ?: base.motivationalQuote,
            weeklyBars = if (actividad.isNotEmpty()) buildWeeklyBars(actividad) else base.weeklyBars
        )
    }

    /** Ids de rutina con al menos una sesión completada cuya fecha local es hoy. */
    private fun completedTodayRoutineIds(sessions: List<Session>): Set<String> {
        val today = LocalDate.now()
        return sessions
            .filter { it.estado.equals("Completada", ignoreCase = true) && localDateOf(it.fechaHoraInicio) == today }
            .mapNotNull { it.idRutina }
            .toSet()
    }

    // fechaHoraInicio viaja en UTC: se convierte a fecha local del dispositivo.
    private fun localDateOf(iso: String): LocalDate? =
        runCatching { Instant.parse(iso).atZone(ZoneId.systemDefault()).toLocalDate() }
            .getOrElse { runCatching { LocalDate.parse(iso.take(10)) }.getOrNull() }

    /**
     * Semana calendario fija de lunes a domingo (como el mock), no una ventana móvil de 7
     * días terminando hoy: esta última hacía que el orden de las barras dependiera del día
     * de la semana en que se mirara el Dashboard.
     */
    private fun buildWeeklyBars(days: List<DailyActivity>): List<DailyComplianceBar> {
        val porFecha = days.associateBy { runCatching { LocalDate.parse(it.fecha.take(10)) }.getOrNull() }
        val maxMinutos = days.maxOfOrNull { it.minutos }?.takeIf { it > 0 }
        val lunesDeEstaSemana = LocalDate.now().let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
        return (0..6).map { offset ->
            val actividad = porFecha[lunesDeEstaSemana.plusDays(offset.toLong())]
            DailyComplianceBar(
                dayLabel = WEEK_DAY_LABELS[offset],
                dayName = WEEK_DAY_NAMES[offset],
                heightFraction = if (actividad != null && maxMinutos != null) {
                    (actividad.minutos / maxMinutos).toFloat()
                } else {
                    0f
                },
                isActive = (actividad?.sesiones ?: 0) > 0
            )
        }
    }

    /**
     * "Mensaje del día" gamificado: celebra constancia (racha de días, sesiones totales),
     * nunca estado del cuerpo — la app no está para afirmar mejoras clínicas (ver alcance del
     * proyecto), y ni siquiera tendría cómo medirlas de forma confiable. Solo compara contra
     * hitos exactos (no "≥") para que sea un evento puntual del día, no un cartel pegado.
     *
     * Dos ejes a propósito: la racha se resetea con una falta y ahí solo devolvería silencio o
     * un genérico justo el día que la persona más necesita aliento; las sesiones totales nunca
     * bajan, así que siguen dando algo que celebrar aunque se haya cortado la racha.
     */
    private fun motivationalMessageFor(rachaDias: Int, sesionesTotales: Int): String {
        RACHA_HITOS[rachaDias]?.let { return it }
        SESIONES_HITOS[sesionesTotales]?.let { return it }
        // Racha recién reiniciada tras un corte (no es la primera sesión de su vida): sin
        // culpa, sin ignorar que pasó — sería peor callarlo justo hoy.
        if (rachaDias == 1 && sesionesTotales > 1) return MENSAJE_VUELTA
        val index = LocalDate.now().dayOfYear % MENSAJES_GENERICOS.size
        return MENSAJES_GENERICOS[index]
    }

    /** Semana (lunes a domingo) sin actividad: se muestra mientras carga o si el reporte falla. */
    private fun buildEmptyWeekBars(): List<DailyComplianceBar> =
        (0..6).map { dayIndex ->
            DailyComplianceBar(
                dayLabel = WEEK_DAY_LABELS[dayIndex],
                dayName = WEEK_DAY_NAMES[dayIndex],
                heightFraction = 0f,
                isActive = false
            )
        }
}

/**
 * Actividad de la semana que informó el servidor más los días en que hay sesiones en el
 * historial del teléfono que ese informe todavía no cuenta: las hechas sin red, o las
 * subidas después de la última vez que se pudo pedir el progreso. Sin esto, quien hizo su
 * rutina sin conexión veía el día de hoy vacío.
 *
 * Solo completa días que el servidor da por inactivos; los que ya trae no se tocan, para no
 * contar dos veces una misma sesión.
 */
internal fun actividadConSesionesLocales(
    delServidor: List<DailyActivity>,
    historial: List<Session>,
    fechaLocal: (String) -> LocalDate?
): List<DailyActivity> {
    val activosEnServidor = delServidor
        .filter { it.sesiones > 0 }
        .mapNotNull { runCatching { LocalDate.parse(it.fecha.take(10)) }.getOrNull() }
        .toSet()
    val locales = historial
        .filter { it.estado.equals("Completada", ignoreCase = true) }
        .mapNotNull { sesion -> fechaLocal(sesion.fechaHoraInicio)?.let { it to sesion } }
        .filter { (fecha, _) -> fecha !in activosEnServidor }
        .groupBy({ it.first }, { it.second })
        .map { (fecha, sesiones) ->
            DailyActivity(
                fecha = fecha.toString(),
                minutos = sesiones.sumOf { it.duracionMinutos },
                sesiones = sesiones.size
            )
        }
    if (locales.isEmpty()) return delServidor
    val fechasLocales = locales.map { it.fecha }.toSet()
    return delServidor.filterNot { it.fecha.take(10) in fechasLocales } + locales
}
