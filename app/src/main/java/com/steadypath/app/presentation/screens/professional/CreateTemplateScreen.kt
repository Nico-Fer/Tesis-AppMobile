package com.steadypath.app.presentation.screens.professional

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiTextField
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.DraftExercise
import com.steadypath.app.presentation.components.ExerciseConfigDialog
import com.steadypath.app.presentation.components.ExercisePicker
import com.steadypath.app.presentation.components.GradientButton
import com.steadypath.app.presentation.components.PrimaryButton

@Composable
fun CreateTemplateScreen(
    onTemplateCreated: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: CreateTemplateViewModel = hiltViewModel()
) {
    val nombre by viewModel.nombre.collectAsStateWithLifecycle()
    val draftExercises by viewModel.draftExercises.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
    val isFormValid by viewModel.isFormValid.collectAsStateWithLifecycle()
    val templateCreated by viewModel.templateCreated.collectAsStateWithLifecycle()

    var showPicker by rememberSaveable { mutableStateOf(false) }
    var exerciseToConfigure by remember { mutableStateOf<Exercise?>(null) }

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
            DomiTopBar(
                title = "Nueva rutina",
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
                    onValueChange = viewModel::onNombreChange,
                    label = "NOMBRE DE LA RUTINA",
                    placeholder = "Ej: Rehabilitación de rodilla"
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
                    Text(
                        text = "Aún no sumaste ejercicios. Tocá el botón de abajo para empezar.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    draftExercises.forEach { draft ->
                        DraftExerciseRow(draft = draft, onRemove = { viewModel.removeExercise(draft.idEjercicio) })
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                PrimaryButton(
                    text = "Agregar ejercicio",
                    onClick = {
                        viewModel.loadCatalog()
                        showPicker = true
                    },
                    contentDesc = "Agregar ejercicio a la plantilla"
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
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                GradientButton(
                    text = "Guardar rutina",
                    onClick = viewModel::saveTemplate,
                    enabled = isFormValid,
                    isLoading = saveState is UiState.Loading,
                    contentDesc = "Guardar plantilla"
                )
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

    if (templateCreated) {
        TemplateCreatedDialog(onConfirm = onTemplateCreated)
    }
}

@Composable
private fun DraftExerciseRow(draft: DraftExercise, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(16.dp)
    ) {
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
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Quitar ${draft.nombre} de la rutina",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TemplateCreatedDialog(onConfirm: () -> Unit) {
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
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
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
                text = "La vas a encontrar en Mis Rutinas.",
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
