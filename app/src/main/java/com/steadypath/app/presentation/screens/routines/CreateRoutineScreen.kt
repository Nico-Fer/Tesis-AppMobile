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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DaySelectorSection
import com.steadypath.app.presentation.components.DomiTextField
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.DraftExercise
import com.steadypath.app.presentation.components.ExerciseConfigDialog
import com.steadypath.app.presentation.components.ExercisePicker
import com.steadypath.app.presentation.components.GradientButton

@Composable
fun CreateRoutineScreen(
    onRoutineCreated: () -> Unit,
    onRecordReference: (exerciseId: String, exerciseName: String, patternSlug: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CreateRoutineViewModel = hiltViewModel()
) {
    val nombre by viewModel.nombre.collectAsStateWithLifecycle()
    val draftExercises by viewModel.draftExercises.collectAsStateWithLifecycle()
    val diasSeleccionados by viewModel.diasSeleccionados.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
    val isFormValid by viewModel.isFormValid.collectAsStateWithLifecycle()
    val routineCreated by viewModel.routineCreated.collectAsStateWithLifecycle()

    var showPicker by rememberSaveable { mutableStateOf(false) }
    var exerciseToConfigure by remember { mutableStateOf<Exercise?>(null) }

    if (showPicker) {
        ExercisePicker(
            catalogState = catalogState,
            onExerciseClick = { exerciseToConfigure = it },
            onNavigateBack = { showPicker = false }
        )
    } else {
        CreateRoutineForm(
            nombre = nombre,
            onNombreChange = viewModel::onNombreChange,
            diasSeleccionados = diasSeleccionados,
            onToggleDia = viewModel::toggleDia,
            onSetTodosLosDias = viewModel::setTodosLosDias,
            draftExercises = draftExercises,
            onRemoveExercise = viewModel::removeExercise,
            onRecordReference = onRecordReference,
            onAddExerciseClick = {
                viewModel.loadCatalog()
                showPicker = true
            },
            saveState = saveState,
            isFormValid = isFormValid,
            onSaveClick = viewModel::saveRoutine,
            onNavigateBack = onNavigateBack
        )
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

    if (routineCreated) {
        RoutineCreatedDialog(onConfirm = onRoutineCreated)
    }
}

@Composable
private fun CreateRoutineForm(
    nombre: String,
    onNombreChange: (String) -> Unit,
    diasSeleccionados: Set<DiaSemana>,
    onToggleDia: (DiaSemana) -> Unit,
    onSetTodosLosDias: (Boolean) -> Unit,
    draftExercises: List<DraftExercise>,
    onRemoveExercise: (String) -> Unit,
    onRecordReference: (String, String, String) -> Unit,
    onAddExerciseClick: () -> Unit,
    saveState: UiState<Unit>,
    isFormValid: Boolean,
    onSaveClick: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            title = "Crear rutina",
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            DomiTextField(
                value = nombre,
                onValueChange = onNombreChange,
                label = "NOMBRE DE LA RUTINA",
                placeholder = "Ej: Ejercicios de la mañana"
            )

            Spacer(modifier = Modifier.height(24.dp))

            DaySelectorSection(
                diasSeleccionados = diasSeleccionados,
                onToggleDia = onToggleDia,
                onSetTodosLosDias = onSetTodosLosDias
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Ejercicios",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (draftExercises.isEmpty()) {
                EmptyDraftCard()
            } else {
                draftExercises.forEach { draft ->
                    DraftExerciseCard(
                        draft = draft,
                        onRemove = { onRemoveExercise(draft.idEjercicio) },
                        onRecordReference = draft.patronMovimiento?.let { slug ->
                            { onRecordReference(draft.idEjercicio, draft.nombre, slug) }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AddExerciseButton(onClick = onAddExerciseClick)

            if (saveState is UiState.Error) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = saveState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Barra inferior fija con el CTA principal (según mock crear_rutina)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            GradientButton(
                text = "Guardar rutina",
                onClick = onSaveClick,
                enabled = isFormValid,
                isLoading = saveState is UiState.Loading,
                contentDesc = "Guardar rutina"
            )
        }
    }
}

@Composable
private fun EmptyDraftCard() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(24.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.FitnessCenter,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Aún no sumaste ejercicios. Tocá el botón de abajo para empezar.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DraftExerciseCard(
    draft: DraftExercise,
    onRemove: () -> Unit,
    onRecordReference: (() -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
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
                    modifier = Modifier.size(22.dp)
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

            IconButton(
                onClick = onRemove,
                modifier = Modifier.semantics {
                    contentDescription = "Quitar ${draft.nombre} de la rutina"
                }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Solo si el ejercicio declara patrón: sin él no se sabe qué ángulos medir.
        if (onRecordReference != null) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onRecordReference,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Grabar referencia de ${draft.nombre}" }
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Grabar referencia")
            }
        }
    }
}

@Composable
private fun AddExerciseButton(onClick: () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { 2.dp.toPx() }
    val dashPx = with(density) { 8.dp.toPx() }
    val cornerPx = with(density) { 12.dp.toPx() }

    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(cornerPx),
                    style = Stroke(
                        width = strokeWidthPx,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, dashPx))
                    )
                )
            }
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp)
            .semantics {
                contentDescription = "Agregar ejercicio a la rutina"
                role = Role.Button
            }
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Agregar ejercicio",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RoutineCreatedDialog(onConfirm: () -> Unit) {
    // Sin dismiss por fuera: la rutina ya se creó, el único camino es volver a Mis Rutinas.
    Dialog(onDismissRequest = onConfirm) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "¡Rutina creada!",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Tu rutina ya está lista. La vas a encontrar en Mis Rutinas.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            GradientButton(
                text = "Entendido",
                onClick = onConfirm,
                contentDesc = "Volver a Mis Rutinas"
            )
        }
    }
}
