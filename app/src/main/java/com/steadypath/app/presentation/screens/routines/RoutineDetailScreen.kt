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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Search
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
import com.steadypath.app.domain.model.RoutineExercise
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.BottomNavTab
import com.steadypath.app.presentation.components.DomiBottomNavBar
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.GradientButton

@Composable
fun RoutineDetailScreen(
    routineId: String,
    onExerciseClick: (String) -> Unit,
    onSeeCatalogClick: () -> Unit,
    onStartRoutine: (exerciseId: String) -> Unit,
    onEditRoutineClick: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: RoutineDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(routineId) {
        viewModel.loadRoutine(routineId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            showLogo = true,
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
        ) {
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
                    Text(
                        text = state.data.nombreRutina,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = state.data.diasAsignados.resumenDias()
                            ?.let { "Los ejercicios de tu rutina · $it" }
                            ?: "Los ejercicios de tu rutina.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(state.data.ejercicios, key = { it.idRutinaEjercicio }) { exercise ->
                            RoutineExerciseCard(
                                exercise = exercise,
                                onClick = {
                                    // Entrar por un ejercicio también arma la secuencia, desde ese paso
                                    viewModel.startRoutineSession(fromIdEjercicio = exercise.idEjercicio)
                                    onExerciseClick(exercise.idEjercicio)
                                }
                            )
                        }
                        item {
                            SeeCatalogButton(onClick = onSeeCatalogClick)
                            // Solo si la creaste vos: una rutina que te asignó tu profesional no se edita desde acá.
                            if (state.data.idProfesionalAsignador == null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                EditRoutineButton(onClick = onEditRoutineClick)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        // Comenzar abre el detalle del primer ejercicio; la secuencia sigue el orden de la rutina.
        val firstExercise = (uiState as? UiState.Success)?.data?.ejercicios?.firstOrNull()
        if (firstExercise != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                GradientButton(
                    text = "Comenzar",
                    onClick = {
                        viewModel.startRoutineSession()
                        onStartRoutine(firstExercise.idEjercicio)
                    },
                    contentDesc = "Comenzar rutina"
                )
            }
        }

        DomiBottomNavBar(
            selectedTab = BottomNavTab.Routines,
            onTabSelected = { }
        )
    }
}

@Composable
private fun RoutineExerciseCard(
    exercise: RoutineExercise,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(onClick = onClick)
            .padding(16.dp)
            .semantics {
                contentDescription = "Ejercicio ${exercise.nombreEjercicio}"
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
                text = exercise.nombreEjercicio,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${exercise.series} series · ${exercise.repeticionesObjetivo} repeticiones",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val zonas = exercise.zonasAfectadas.joinToString(", ")
            // La duración estimada no se muestra; sigue en el modelo por si vuelve.
            val metadata = listOfNotNull(
                zonas.takeIf { it.isNotBlank() },
                exercise.nivelDificultad.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (metadata.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SeeCatalogButton(onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
            .semantics {
                contentDescription = "Ver catálogo completo de ejercicios"
                role = Role.Button
            }
    ) {
        Icon(
            imageVector = Icons.Filled.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Ver catálogo completo de ejercicios",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Solo para rutinas creadas por el propio paciente: una rutina asignada la edita el profesional. */
@Composable
private fun EditRoutineButton(onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
            .semantics {
                contentDescription = "Editar rutina"
                role = Role.Button
            }
    ) {
        Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Editar rutina",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}
