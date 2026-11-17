package com.steadypath.app.presentation.screens.professional

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.PainRecord
import com.steadypath.app.domain.model.Session
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.ProfessionalBottomNavBar
import com.steadypath.app.presentation.components.ProfessionalNavTab
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PatientDetailScreen(
    patientId: String,
    patientName: String,
    edad: Int?,
    onViewRoutinesClick: () -> Unit,
    onAssignRoutineClick: () -> Unit,
    onRecordReferenceClick: () -> Unit,
    onHomeClick: () -> Unit,
    onPatientsClick: () -> Unit,
    onRoutinesClick: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PatientDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Se recarga en cada entrada: al volver de asignar una rutina hay que reflejarla.
    LaunchedEffect(patientId) {
        viewModel.loadPatient(patientId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            title = patientName,
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Resumen") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Historial") }
            )
        }

        when (val state = uiState) {
            is UiState.Loading -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            is UiState.Error -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            is UiState.Success -> if (selectedTab == 0) {
                ResumenTab(
                    patientName = patientName,
                    edad = edad,
                    data = state.data,
                    onViewRoutinesClick = onViewRoutinesClick,
                    onAssignRoutineClick = onAssignRoutineClick,
                    onRecordReferenceClick = onRecordReferenceClick,
                    modifier = Modifier.weight(1f)
                )
            } else {
                HistorialTab(
                    sessions = state.data.historialReciente,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        ProfessionalBottomNavBar(
            selectedTab = ProfessionalNavTab.Patients,
            onTabSelected = { tab ->
                when (tab) {
                    ProfessionalNavTab.Home -> onHomeClick()
                    ProfessionalNavTab.Patients -> onPatientsClick()
                    ProfessionalNavTab.Routines -> onRoutinesClick()
                }
            }
        )
    }
}

@Composable
private fun ResumenTab(
    patientName: String,
    edad: Int?,
    data: PatientDetailData,
    onViewRoutinesClick: () -> Unit,
    onAssignRoutineClick: () -> Unit,
    onRecordReferenceClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(52.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = patientName,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            val rutinaActual = data.rutinasActivas.firstOrNull()?.nombreRutina
            val subtitulo = listOfNotNull(
                edad?.let { "$it años" },
                rutinaActual?.let { "Rutina actual: $it" }
            ).joinToString(" · ")
            if (subtitulo.isNotBlank()) {
                Text(
                    text = subtitulo,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), modifier = Modifier.fillMaxWidth()) {
            ComplianceCard(porcentaje = data.compliance?.porcentajeCumplimiento, modifier = Modifier.weight(1f))
            SesionesCard(cantidad = data.totalSesiones, modifier = Modifier.weight(1f))
        }

        if (data.evolucionDolor.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            PainEvolutionCard(registros = data.evolucionDolor)
        }

        Spacer(modifier = Modifier.height(24.dp))

        SolidActionButton(
            text = if (data.rutinasActivas.size == 1) "Ver rutina asignada" else "Ver rutinas asignadas",
            icon = Icons.AutoMirrored.Filled.Assignment,
            onClick = onViewRoutinesClick,
            contentDesc = "Ver las rutinas asignadas a este paciente"
        )

        Spacer(modifier = Modifier.height(16.dp))

        SolidActionButton(
            text = "Asignar rutina",
            icon = Icons.Filled.Add,
            onClick = onAssignRoutineClick,
            contentDesc = "Asignar una rutina nueva a este paciente"
        )

        Spacer(modifier = Modifier.height(16.dp))

        SolidActionButton(
            text = "Grabar referencia",
            icon = Icons.Filled.Videocam,
            onClick = onRecordReferenceClick,
            contentDesc = "Grabar la referencia de movimiento de un ejercicio para este paciente"
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SolidActionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    contentDesc: String
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick)
            .semantics { contentDescription = contentDesc }
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary
        )
    }
}

private data class HistorialEntry(
    val fechaHoraInicio: String,
    val duracionMinutos: Double,
    val nombreEjercicio: String,
    val escalaEVA: Int?
)

@Composable
private fun HistorialTab(
    sessions: List<Session>,
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "Aún no hizo sesiones.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val entradas = sessions.flatMap { session ->
        session.resultados.map { resultado ->
            HistorialEntry(
                fechaHoraInicio = session.fechaHoraInicio,
                duracionMinutos = session.duracionMinutos,
                nombreEjercicio = resultado.nombreEjercicio ?: "Ejercicio",
                escalaEVA = resultado.escalaEVA
            )
        }
    }
    val grupos = entradas.groupBy { grupoSemana(it.fechaHoraInicio) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Historial de sesiones",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${sessions.size} sesiones completadas",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        listOf("Esta semana", "Semana pasada", "Anteriores").forEach { grupo ->
            val items = grupos[grupo].orEmpty()
            if (items.isNotEmpty()) {
                Text(
                    text = grupo,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                items.forEach { entrada ->
                    HistorialEntryRow(entrada)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun grupoSemana(iso: String): String {
    val fecha = runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    }.getOrNull() ?: return "Anteriores"
    val hoy = LocalDate.now()
    val dias = java.time.temporal.ChronoUnit.DAYS.between(fecha, hoy)
    return when {
        dias < 7 -> "Esta semana"
        dias < 14 -> "Semana pasada"
        else -> "Anteriores"
    }
}

@Composable
private fun HistorialEntryRow(entrada: HistorialEntry) {
    val eva = entrada.escalaEVA
    val esAlerta = eva != null && eva >= 7
    val colorEva = if (esAlerta) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entrada.nombreEjercicio,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${formatFecha(entrada.fechaHoraInicio)} · ${entrada.duracionMinutos.toInt()} min",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (eva != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(colorEva.copy(alpha = 0.1f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "DOLOR $eva/10",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorEva,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ComplianceCard(porcentaje: Double?, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(32.dp)
    ) {
        Text(
            text = if (porcentaje != null) "${porcentaje.toInt()}%" else "—",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "CUMPLIMIENTO",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SesionesCard(cantidad: Int, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(32.dp)
    ) {
        Text(
            text = cantidad.toString(),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "SESIONES",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PainEvolutionCard(registros: List<PainRecord>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(32.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = "Evolución del dolor",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Últimas ${registros.size} sesiones",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${registros.last().escalaEVA}/10",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ACTUAL",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth().height(180.dp)) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxHeight().padding(end = 8.dp)
            ) {
                Text("10", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("5", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PainLineChart(
                registros = registros,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind {
                        val strokeColor = Color(0xFFD8DAE2)
                        drawLine(strokeColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2f)
                        drawLine(strokeColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
                    }
            )
        }
    }
}

@Composable
private fun PainLineChart(registros: List<PainRecord>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.secondary
    Canvas(modifier = modifier.padding(start = 8.dp)) {
        if (registros.size < 2) return@Canvas
        val stepX = size.width / (registros.size - 1)
        fun yFor(eva: Int) = size.height - (eva / 10f) * size.height

        val path = Path()
        registros.forEachIndexed { index, registro ->
            val x = index * stepX
            val y = yFor(registro.escalaEVA)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        registros.forEachIndexed { index, registro ->
            drawCircle(lineColor, radius = 6f, center = Offset(index * stepX, yFor(registro.escalaEVA)))
        }
    }
}

// El backend guarda UTC; si el string no trae offset se asume UTC y se pasa a fecha local.
private fun formatFecha(iso: String): String = runCatching {
    val date = OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    when (date) {
        today -> "Hoy"
        today.minusDays(1) -> "Ayer"
        else -> date.format(DateTimeFormatter.ofPattern("d MMM", Locale("es")))
    }
}.getOrDefault("")
