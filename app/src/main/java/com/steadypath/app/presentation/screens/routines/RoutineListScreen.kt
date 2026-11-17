package com.steadypath.app.presentation.screens.routines

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Routine
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.BottomNavTab
import com.steadypath.app.presentation.components.DomiBottomNavBar
import com.steadypath.app.presentation.components.DomiMainHeader
import com.steadypath.app.presentation.components.PrimaryButton

/** "Todos los días", "L · X · V" o null si la rutina no tiene días fijos. */
internal fun List<DiaSemana>.resumenDias(): String? = when {
    isEmpty() -> null
    size == DiaSemana.entries.size -> "Todos los días"
    else -> sortedBy { it.ordinal }.joinToString(" · ") { it.etiqueta }
}

@Composable
fun RoutineListScreen(
    onRoutineClick: (String) -> Unit,
    onCreateRoutineClick: () -> Unit,
    onProgressClick: () -> Unit,
    onNavigateBack: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: RoutineListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val patientName by viewModel.patientName.collectAsStateWithLifecycle()

    // Se recarga en cada entrada: al volver de crear una rutina la lista debe refrescar.
    LaunchedEffect(Unit) {
        viewModel.loadRoutines()
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
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Mis Rutinas",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Las rutinas que indicó tu profesional.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (val state = uiState) {
                is UiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                is UiState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
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
                    if (state.data.isEmpty()) {
                        EmptyRoutines(onCreateRoutineClick = onCreateRoutineClick)
                    } else {
                        RoutineListContent(
                            routines = state.data,
                            onRoutineClick = onRoutineClick,
                            onCreateRoutineClick = onCreateRoutineClick
                        )
                    }
                }
            }
        }

        DomiBottomNavBar(
            selectedTab = BottomNavTab.Routines,
            onTabSelected = { tab ->
                when (tab) {
                    BottomNavTab.Home -> onNavigateBack()
                    BottomNavTab.Progress -> onProgressClick()
                    else -> {}
                }
            }
        )
    }
}

@Composable
private fun RoutineListContent(
    routines: List<Routine>,
    onRoutineClick: (String) -> Unit,
    onCreateRoutineClick: () -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(routines, key = { it.idRutina }) { routine ->
            RoutineCard(routine = routine, onClick = { onRoutineClick(routine.idRutina) })
        }
        item {
            PrimaryButton(
                text = "Crear rutina",
                onClick = onCreateRoutineClick,
                contentDesc = "Crear una rutina nueva"
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    onClick: () -> Unit
) {
    val assignedByProfessional = routine.idProfesionalAsignador != null
    val diasLabel = routine.diasAsignados.resumenDias()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(16.dp)
            .semantics {
                contentDescription = "Rutina ${routine.nombreRutina}"
                role = Role.Button
            }
    ) {
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
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = routine.nombreRutina,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (diasLabel != null) "${routine.ejercicios.size} ejercicios · $diasLabel"
                       else "${routine.ejercicios.size} ejercicios",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            OriginBadge(assignedByProfessional = assignedByProfessional)
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OriginBadge(assignedByProfessional: Boolean) {
    val (background, textColor, label) = if (assignedByProfessional) {
        Triple(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
            MaterialTheme.colorScheme.primary,
            "ASIGNADA POR PROFESIONAL"
        )
    } else {
        Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.secondary,
            "CREADA POR VOS"
        )
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptyRoutines(onCreateRoutineClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Assignment,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Aún no tenés rutinas",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Tu profesional puede asignarte una, o podés crear la tuya.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        PrimaryButton(
            text = "Crear rutina",
            onClick = onCreateRoutineClick,
            contentDesc = "Crear una rutina nueva"
        )
    }
}
