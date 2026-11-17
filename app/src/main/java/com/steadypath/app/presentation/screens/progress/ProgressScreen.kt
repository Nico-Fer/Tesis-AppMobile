package com.steadypath.app.presentation.screens.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.DailyActivity
import com.steadypath.app.domain.model.ProgressReport
import com.steadypath.app.domain.model.RecentSession
import com.steadypath.app.domain.model.ZoneProgress
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.BottomNavTab
import com.steadypath.app.presentation.components.DomiBottomNavBar
import com.steadypath.app.presentation.components.DomiMainHeader
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(
    onNavigateBack: () -> Unit,
    onRoutinesClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ProgressViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val patientName by viewModel.patientName.collectAsStateWithLifecycle()

    // Se recarga en cada entrada: al volver de una sesión el reporte debe refrescar.
    LaunchedEffect(Unit) {
        viewModel.loadProgress()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiMainHeader(nombre = patientName, onSettingsClick = onSettingsClick)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Progreso",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Seguí tu ritmo, celebrá cada paso.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (val state = uiState) {
                is UiState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                is UiState.Error -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 96.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                is UiState.Success -> {
                    if (state.data.sesionesTotales == 0) {
                        EmptyProgress()
                    } else {
                        ProgressContent(report = state.data)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        val onTabSelected = remember(onNavigateBack, onRoutinesClick) {
            { tab: BottomNavTab ->
                when (tab) {
                    BottomNavTab.Home -> onNavigateBack()
                    BottomNavTab.Routines -> onRoutinesClick()
                    else -> {}
                }
            }
        }
        DomiBottomNavBar(
            selectedTab = BottomNavTab.Progress,
            onTabSelected = onTabSelected
        )
    }
}

// ---------- Estado vacío ----------

@Composable
private fun EmptyProgress() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.size(56.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Aún no hay datos para mostrar",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Cuando completes tu primera sesión, acá vas a ver tu progreso.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ---------- Contenido con datos ----------

@Composable
private fun ProgressContent(report: ProgressReport) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(value = "${report.rachaDias}", label = "DÍAS SEGUIDOS", modifier = Modifier.weight(1f))
        StatCard(value = "${report.sesionesTotales}", label = "SESIONES", modifier = Modifier.weight(1f))
        StatCard(value = formatTotalTime(report.minutosTotales), label = "TIEMPO TOTAL", modifier = Modifier.weight(1f))
    }

    Spacer(modifier = Modifier.height(12.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(value = "${report.diasActivos}", label = "DÍAS ACTIVOS", modifier = Modifier.weight(1f))
        StatCard(value = "${report.ejerciciosRealizados}", label = "EJERCICIOS", modifier = Modifier.weight(1f))
        StatCard(
            value = report.promedioEva?.let { String.format(Locale.US, "%.1f", it) } ?: "—",
            label = "DOLOR PROM.",
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(modifier = Modifier.height(32.dp))

    WeeklyActivitySection(days = report.actividadSemanal)

    if (report.progresoPorZona.isNotEmpty()) {
        Spacer(modifier = Modifier.height(32.dp))
        ZoneProgressSection(zones = report.progresoPorZona)
    }

    if (report.historialReciente.isNotEmpty()) {
        Spacer(modifier = Modifier.height(32.dp))
        RecentHistorySection(sessions = report.historialReciente)
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(vertical = 20.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

// ---------- Actividad semanal ----------

@Composable
private fun WeeklyActivitySection(days: List<DailyActivity>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(20.dp)
    ) {
        Text(
            text = "Actividad semanal",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Semana calendario fija de lunes a domingo: el backend devuelve los últimos 7 días
        // terminando hoy, y mostrarlos en ese orden hacía que las barras arrancaran en
        // cualquier día según cuándo se mirara la pantalla.
        val porFecha = days.associateBy { runCatching { LocalDate.parse(it.fecha.take(10)) }.getOrNull() }
        val maxMinutos = days.maxOfOrNull { it.minutos }?.takeIf { it > 0 } ?: 1.0
        val lunesDeEstaSemana = LocalDate.now().let { it.minusDays((it.dayOfWeek.value - 1).toLong()) }
        val semana = (0..6).map { offset ->
            val fecha = lunesDeEstaSemana.plusDays(offset.toLong())
            fecha to porFecha[fecha]
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            semana.forEach { (fecha, day) ->
                val minutos = day?.minutos ?: 0.0
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    val fraction = (minutos / maxMinutos).toFloat()
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(if (minutos > 0) (112 * fraction).coerceAtLeast(8f).dp else 4.dp)
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                            .background(
                                if (minutos > 0) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = DAY_LETTERS.getValue(fecha.dayOfWeek),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------- Progreso por zona ----------

@Composable
private fun ZoneProgressSection(zones: List<ZoneProgress>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Progreso por zona",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        zones.chunked(2).forEach { rowZones ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowZones.forEach { zone ->
                    ZoneCard(zone = zone, modifier = Modifier.weight(1f))
                }
                if (rowZones.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ZoneCard(zone: ZoneProgress, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val percent = zone.porcentajeCumplimiento.coerceIn(0.0, 100.0)
        val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
        val progressColor = MaterialTheme.colorScheme.secondary

        Box(contentAlignment = Alignment.Center) {
            ProgressRing(
                fraction = (percent / 100.0).toFloat(),
                trackColor = trackColor,
                progressColor = progressColor,
                modifier = Modifier.size(96.dp)
            )
            Text(
                text = "${percent.roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = zone.zona,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ProgressRing(
    fraction: Float,
    trackColor: Color,
    progressColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2
        val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)

        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke
        )
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 360f * fraction.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = stroke
        )
    }
}

// ---------- Historial reciente ----------

@Composable
private fun RecentHistorySection(sessions: List<RecentSession>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Historial reciente",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(16.dp))

        sessions.forEach { session ->
            SessionHistoryCard(session = session)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SessionHistoryCard(session: RecentSession) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.nombreRutina
                        ?: session.resultados.firstOrNull()?.nombreEjercicio
                        ?: "Sesión de ejercicios",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatRelativeDate(session.fechaHoraInicio)} • ${session.duracionMinutos.roundToInt()} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val sessionEva = session.resultados.mapNotNull { it.escalaEVA }
                .takeIf { it.isNotEmpty() }
                ?.average()
                ?.roundToInt()
            if (sessionEva != null) {
                PainBadge(eva = sessionEva)
            }
        }

        // EVA por ejercicio: solo tiene sentido desglosar con más de un ejercicio.
        if (session.resultados.size > 1) {
            Spacer(modifier = Modifier.height(12.dp))
            session.resultados.forEach { resultado ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 64.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = resultado.nombreEjercicio ?: "Ejercicio",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (resultado.escalaEVA != null) {
                        Text(
                            text = "Dolor ${resultado.escalaEVA}/10",
                            style = MaterialTheme.typography.labelMedium,
                            color = painColor(resultado.escalaEVA),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PainBadge(eva: Int) {
    val color = painColor(eva)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "Dolor $eva/10",
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun painColor(eva: Int): Color = when {
    eva <= 3 -> MaterialTheme.colorScheme.secondary
    eva <= 6 -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.error
}

// ---------- Formato de fechas y tiempos ----------

private fun formatTotalTime(minutos: Double): String =
    if (minutos >= 60) String.format(Locale.US, "%.1f h", minutos / 60.0)
    else "${minutos.roundToInt()} min"

private val DAY_LETTERS = mapOf(
    DayOfWeek.MONDAY to "L",
    DayOfWeek.TUESDAY to "M",
    DayOfWeek.WEDNESDAY to "X",
    DayOfWeek.THURSDAY to "J",
    DayOfWeek.FRIDAY to "V",
    DayOfWeek.SATURDAY to "S",
    DayOfWeek.SUNDAY to "D"
)

/** El backend guarda UTC; si el string no trae offset se asume UTC y se pasa a hora local. */
private fun parseToLocalDate(iso: String): LocalDate? = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
}.recoverCatching {
    LocalDateTime.parse(iso)
        .atOffset(ZoneOffset.UTC)
        .atZoneSameInstant(ZoneId.systemDefault())
        .toLocalDate()
}.getOrNull()

private fun formatRelativeDate(iso: String): String {
    val date = parseToLocalDate(iso) ?: return ""
    val today = LocalDate.now()
    return when (date) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale("es")))
    }
}
