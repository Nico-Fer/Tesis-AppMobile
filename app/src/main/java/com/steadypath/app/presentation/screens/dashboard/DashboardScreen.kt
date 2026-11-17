package com.steadypath.app.presentation.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.steadypath.app.domain.model.Invitation
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.screens.dashboard.DailyComplianceBar
import com.steadypath.app.presentation.components.BottomNavTab
import com.steadypath.app.presentation.components.GradientButton
import com.steadypath.app.presentation.components.DomiBottomNavBar
import com.steadypath.app.presentation.components.DomiMainHeader

@Composable
fun DashboardScreen(
    onRoutinesClick: () -> Unit,
    onProgressClick: () -> Unit,
    onTodayRoutineClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val patientName by viewModel.patientName.collectAsStateWithLifecycle()

    // Recarga en cada entrada para reflejar una sesión recién completada.
    LaunchedEffect(Unit) {
        viewModel.loadDashboard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiMainHeader(nombre = patientName, onSettingsClick = onSettingsClick)

        when (val state = uiState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is UiState.Success<*> -> {
                @Suppress("UNCHECKED_CAST")
                DashboardContent(
                    data = state.data as DashboardData,
                    onTodayRoutineClick = onTodayRoutineClick,
                    onAcceptInvitation = viewModel::acceptInvitation,
                    onRejectInvitation = viewModel::rejectInvitation,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        val onTabSelected = remember(onRoutinesClick, onProgressClick) {
            { tab: BottomNavTab ->
                when (tab) {
                    BottomNavTab.Routines -> onRoutinesClick()
                    BottomNavTab.Progress -> onProgressClick()
                    else -> {}
                }
            }
        }
        DomiBottomNavBar(
            selectedTab = BottomNavTab.Home,
            onTabSelected = onTabSelected
        )
    }
}

@Composable
private fun DashboardContent(
    data: DashboardData,
    onTodayRoutineClick: (String) -> Unit,
    onAcceptInvitation: (String) -> Unit,
    onRejectInvitation: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = "Inicio",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        data.pendingInvitations.forEach { invitation ->
            InvitationCard(
                invitation = invitation,
                onAccept = { onAcceptInvitation(invitation.idInvitacion) },
                onReject = { onRejectInvitation(invitation.idInvitacion) }
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Motivational quote section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(20.dp)
        ) {
            Text(
                text = "MENSAJE DEL DÍA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "\"${data.motivationalQuote}\"",
                style = MaterialTheme.typography.headlineSmall.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Weekly progress section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tu progreso semanal",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = data.compliance?.porcentajeCumplimiento?.let { "${it.toInt()}%" } ?: "--",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weekly bar chart
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(20.dp)
        ) {
            WeeklyProgressBars(
                bars = data.weeklyBars,
                compliancePercent = data.compliance?.porcentajeCumplimiento ?: 0.0
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Today's routines section
        Text(
            text = if (data.todayRoutines.size > 1) "RUTINAS DE HOY" else "RUTINA DE HOY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (data.todayRoutines.isEmpty()) {
            NoRoutinesTodayCard()
        } else {
            data.todayRoutines.forEach { routine ->
                TodayRoutineCard(
                    routine = routine,
                    onClick = { onTodayRoutineClick(routine.idRutina) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // RNF-17: la app guía la práctica y no reemplaza al profesional. Sin palabras de la
        // lista vedada del RNF-17 ("diagnóstico", "evaluación"...), ni siquiera negadas.
        Text(
            text = "Esta app te guía en tus ejercicios. No reemplaza a tu profesional de salud.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InvitationCard(
    invitation: Invitation,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color = MaterialTheme.colorScheme.primary, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "El usuario ${invitation.nombreProfesional.orEmpty()} quiere seguir tu profesional",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Te invitó a vincularte con su cuenta.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onAccept)
            ) {
                Text(
                    text = "Aceptar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    .clickable(onClick = onReject)
            ) {
                Text(
                    text = "Rechazar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun NoRoutinesTodayCard() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(24.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.FitnessCenter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No tenés rutinas para hoy",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Podés entrenar igual desde Mis Rutinas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TodayRoutineCard(
    routine: TodayRoutine,
    onClick: () -> Unit
) {
    val estadoLabel = if (routine.completadaHoy) "completada hoy" else "pendiente"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(20.dp)
            .semantics { contentDescription = "Rutina de hoy: ${routine.nombre}, $estadoLabel" }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = routine.nombre,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                routine.diasLabel?.let { dias ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dias,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (routine.completadaHoy) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Completada",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        GradientButton(
            text = "Continuar ▶",
            onClick = onClick,
            contentDesc = if (routine.completadaHoy) "Repetir la rutina ${routine.nombre}" else "Comenzar la rutina ${routine.nombre}"
        )
    }
}

@Composable
private fun WeeklyProgressBars(
    bars: List<DailyComplianceBar>,
    compliancePercent: Double
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .semantics { contentDescription = "Gráfico de progreso semanal: ${compliancePercent.toInt()} por ciento" },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom
    ) {
        bars.forEach { bar ->
            // Piso de 8dp para que un día con poca actividad no desaparezca; 4dp marca el día vacío.
            val barHeight = if (bar.heightFraction > 0f) (bar.heightFraction * 80).coerceAtLeast(8f).dp else 4.dp
            val stateLabel = if (bar.isActive) "con actividad" else "sin actividad"

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = "${bar.dayName}: $stateLabel"
                }
            ) {
                if (bar.isActive) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                } else {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(
                            if (bar.isActive) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = bar.dayLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bar.isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
