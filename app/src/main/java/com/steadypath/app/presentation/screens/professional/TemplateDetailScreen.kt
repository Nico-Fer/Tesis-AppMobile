package com.steadypath.app.presentation.screens.professional

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.domain.model.RoutineTemplate
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DashedAddButton
import com.steadypath.app.presentation.components.DomiTextField
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.DraftExercise
import com.steadypath.app.presentation.components.ExerciseConfigDialog
import com.steadypath.app.presentation.components.ExercisePicker
import com.steadypath.app.presentation.components.GradientButton

@Composable
fun TemplateDetailScreen(
    templateId: String,
    onAssignClick: (templateId: String, templateName: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: TemplateDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isEditing by viewModel.isEditing.collectAsStateWithLifecycle()

    LaunchedEffect(templateId) { viewModel.loadTemplate(templateId) }

    when (val state = uiState) {
        is UiState.Loading -> Box(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }

        is UiState.Error -> Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            DomiTopBar(title = "Rutina", showBackArrow = true, onBackClick = onNavigateBack)
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }

        is UiState.Success -> if (isEditing) {
            EditTemplateBody(
                templateId = templateId,
                viewModel = viewModel,
                onNavigateBack = { viewModel.cancelEditing() }
            )
        } else {
            TemplateReadOnlyBody(
                template = state.data,
                onAssignClick = { onAssignClick(templateId, state.data.nombreRutina) },
                onEditClick = { viewModel.startEditing() },
                onNavigateBack = onNavigateBack
            )
        }
    }
}

@Composable
private fun TemplateReadOnlyBody(
    template: RoutineTemplate,
    onAssignClick: () -> Unit,
    onEditClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(title = "Mis Rutinas", showBackArrow = true, onBackClick = onNavigateBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = template.nombreRutina,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${template.ejercicios.size} ejercicios",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Ejercicios de la rutina",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))

            template.ejercicios.forEach { ejercicio ->
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
                            text = ejercicio.nombreEjercicio,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${ejercicio.series} series · ${ejercicio.repeticionesObjetivo} repeticiones",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            GradientButton(
                text = "Asignar a un Usuario",
                onClick = onAssignClick,
                contentDesc = "Asignar esta plantilla a un paciente"
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    .clickable(onClick = onEditClick)
                    .semantics { contentDescription = "Editar rutina" }
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Editar rutina",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun EditTemplateBody(
    templateId: String,
    viewModel: TemplateDetailViewModel,
    onNavigateBack: () -> Unit
) {
    val nombre by viewModel.nombre.collectAsStateWithLifecycle()
    val draftExercises by viewModel.draftExercises.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()

    var showPicker by rememberSaveable { mutableStateOf(false) }
    var exerciseToConfigure by remember { mutableStateOf<Exercise?>(null) }
    var exerciseBeingEdited by remember { mutableStateOf<DraftExercise?>(null) }

    if (showPicker) {
        ExercisePicker(
            catalogState = catalogState,
            onExerciseClick = { exerciseToConfigure = it },
            onNavigateBack = { showPicker = false }
        )
        return
    }

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
            DomiTextField(
                value = nombre,
                onValueChange = viewModel::onNombreChange,
                label = "NOMBRE DE LA RUTINA",
                placeholder = "Ej: Rehabilitación de rodilla"
            )

            Spacer(modifier = Modifier.height(24.dp))

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
                onClick = { viewModel.saveEdits(templateId) },
                isLoading = saveState is UiState.Loading,
                contentDesc = "Guardar cambios de la rutina"
            )

            Spacer(modifier = Modifier.height(24.dp))
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
}
