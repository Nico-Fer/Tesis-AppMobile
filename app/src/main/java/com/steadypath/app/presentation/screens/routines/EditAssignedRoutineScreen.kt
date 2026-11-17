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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DashedAddButton
import com.steadypath.app.presentation.components.DaySelectorSection
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.DraftExercise
import com.steadypath.app.presentation.components.ExerciseConfigDialog
import com.steadypath.app.presentation.components.ExercisePicker
import com.steadypath.app.presentation.components.GradientButton

@Composable
fun EditAssignedRoutineScreen(
    routineId: String,
    onRoutineDeactivated: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: EditAssignedRoutineViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val diasSeleccionados by viewModel.diasSeleccionados.collectAsStateWithLifecycle()
    val draftExercises by viewModel.draftExercises.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
    val routineDeactivated by viewModel.routineDeactivated.collectAsStateWithLifecycle()

    var showPicker by rememberSaveable { mutableStateOf(false) }
    var exerciseToConfigure by remember { mutableStateOf<Exercise?>(null) }
    var exerciseBeingEdited by remember { mutableStateOf<DraftExercise?>(null) }
    var showDeactivateConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(routineId) { viewModel.loadRoutine(routineId) }

    LaunchedEffect(routineDeactivated) {
        if (routineDeactivated) onRoutineDeactivated()
    }

    when (val state = uiState) {
        is UiState.Loading -> Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        is UiState.Error -> Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            DomiTopBar(title = "Editar Rutina", showBackArrow = true, onBackClick = onNavigateBack)
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }

        is UiState.Success -> {
            if (showPicker) {
                ExercisePicker(
                    catalogState = catalogState,
                    onExerciseClick = { exerciseToConfigure = it },
                    onNavigateBack = { showPicker = false }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    DomiTopBar(title = "Editar Rutina", showBackArrow = true, onBackClick = onNavigateBack)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = state.data.nombreRutina,
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        DaySelectorSection(
                            diasSeleccionados = diasSeleccionados,
                            onToggleDia = viewModel::toggleDia,
                            onSetTodosLosDias = viewModel::setTodosLosDias
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "Ejercicios de la rutina",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        draftExercises.forEach { draft ->
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
                                        imageVector = Icons.Filled.FitnessCenter,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = draft.nombre,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${draft.series} series · ${draft.repeticiones} repeticiones",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { exerciseBeingEdited = draft }) {
                                    Icon(
                                        imageVector = Icons.Filled.Edit,
                                        contentDescription = "Editar ${draft.nombre}",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { viewModel.removeExercise(draft.idEjercicio) }) {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Quitar ${draft.nombre}",
                                        tint = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        DashedAddButton(
                            text = "Agregar ejercicio",
                            onClick = {
                                viewModel.loadCatalog()
                                showPicker = true
                            },
                            contentDesc = "Agregar ejercicio a la rutina"
                        )

                        if (saveState is UiState.Error) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = (saveState as UiState.Error).message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        GradientButton(
                            text = "Guardar cambios",
                            onClick = { viewModel.saveChanges(routineId) },
                            isLoading = saveState is UiState.Loading,
                            contentDesc = "Guardar cambios de la rutina"
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { showDeactivateConfirm = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Desactivar rutina", color = MaterialTheme.colorScheme.tertiary)
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            exerciseToConfigure?.let { exercise ->
                ExerciseConfigDialog(
                    exercise = exercise,
                    onConfirm = { series, repeticiones ->
                        viewModel.addExercise(exercise, series, repeticiones)
                        exerciseToConfigure = null
                        showPicker = false
                    },
                    onDismiss = { exerciseToConfigure = null }
                )
            }

            exerciseBeingEdited?.let { draft ->
                ExerciseConfigDialog(
                    exercise = Exercise(
                        idEjercicio = draft.idEjercicio,
                        nombre = draft.nombre,
                        descripcion = "",
                        urlVideoExplicativo = null,
                        zonasAfectadas = emptyList(),
                        nivelDificultad = "",
                        duracionMinutos = 0
                    ),
                    initialSeries = draft.series,
                    initialRepeticiones = draft.repeticiones,
                    isEditing = true,
                    onConfirm = { series, repeticiones ->
                        viewModel.updateExercise(draft.idEjercicio, series, repeticiones)
                        exerciseBeingEdited = null
                    },
                    onDismiss = { exerciseBeingEdited = null }
                )
            }

            if (showDeactivateConfirm) {
                DeactivateConfirmDialog(
                    onConfirm = {
                        showDeactivateConfirm = false
                        viewModel.deactivateRoutine(routineId)
                    },
                    onDismiss = { showDeactivateConfirm = false }
                )
            }
        }
    }
}

@Composable
private fun DeactivateConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "¿Desactivar esta rutina?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "La rutina dejará de estar activa, pero vas a poder volver a activarla más adelante.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.tertiaryContainer)
                    .clickable(onClick = onConfirm)
                    .semantics { contentDescription = "Sí, desactivar la rutina" }
            ) {
                Text(
                    text = "Sí, desactivar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    }
}
