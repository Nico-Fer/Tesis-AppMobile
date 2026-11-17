package com.steadypath.app.presentation.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.steadypath.app.domain.model.DiaSemana
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.presentation.common.UiState

/**
 * Ejercicio elegido para una rutina en construcción.
 * Compartido entre la creación de rutina propia del paciente y la asignación del profesional.
 */
data class DraftExercise(
    val idEjercicio: String,
    val nombre: String,
    val series: Int,
    val repeticiones: Int,
    /** Slug del patrón de movimiento; sin él no se puede grabar referencia del ejercicio. */
    val patronMovimiento: String? = null
)

@Composable
fun ExercisePicker(
    catalogState: UiState<List<Exercise>>,
    onExerciseClick: (Exercise) -> Unit,
    onNavigateBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            title = "Catálogo",
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Todos los ejercicios",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Mirá todos los ejercicios.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (catalogState) {
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
                        text = catalogState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                is UiState.Success -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(catalogState.data, key = { it.idEjercicio }) { exercise ->
                        CatalogExerciseCard(
                            exercise = exercise,
                            onClick = { onExerciseClick(exercise) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

@Composable
fun CatalogExerciseCard(
    exercise: Exercise,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
            .semantics {
                contentDescription = "Agregar ${exercise.nombre}"
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
                text = exercise.nombre,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // La duración estimada del catálogo no se muestra; sigue en el modelo por si vuelve.
                val detalle = exercise.zonasAfectadas.joinToString(", ")
                if (detalle.isNotBlank()) {
                    Text(
                        text = detalle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        text = " · ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = exercise.nivelDificultad,
                    style = MaterialTheme.typography.bodyMedium,
                    color = dificultadColor(exercise.nivelDificultad),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline
        )
    }
}

/**
 * Colorea el nivel de dificultad como un semáforo: fácil = verde, medio = naranja, difícil = rojo.
 * Es color de TEXTO: cada valor tiene que contrastar al menos 4,5:1 contra la tarjeta (WCAG 1.4.3).
 * Los *Container de la paleta son colores de fondo; usados como texto no se leen (1,4:1).
 */
@Composable
fun dificultadColor(nivelDificultad: String): androidx.compose.ui.graphics.Color {
    val normalizado = nivelDificultad.trim().lowercase()
    return when {
        normalizado.startsWith("facil") || normalizado.startsWith("fácil") -> MaterialTheme.colorScheme.secondary
        normalizado.startsWith("dificil") || normalizado.startsWith("difícil") -> MaterialTheme.colorScheme.error
        normalizado.startsWith("medi") -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun ExerciseConfigDialog(
    exercise: Exercise,
    onConfirm: (series: Int, repeticiones: Int) -> Unit,
    onDismiss: () -> Unit,
    initialSeries: Int = 3,
    initialRepeticiones: Int = 10,
    isEditing: Boolean = false
) {
    var series by rememberSaveable { mutableStateOf(initialSeries) }
    var repeticiones by rememberSaveable { mutableStateOf(initialRepeticiones) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = exercise.nombre,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { contentDescription = "Cerrar" }
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isEditing) "¿Qué ajustes querés realizar?" else "¿Cuántas querés asignar?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            StepperRow(
                label = "Series",
                value = series,
                onDecrement = { if (series > 1) series-- },
                onIncrement = { series++ }
            )

            Spacer(modifier = Modifier.height(16.dp))

            StepperRow(
                label = "Repeticiones",
                value = repeticiones,
                onDecrement = { if (repeticiones > 1) repeticiones-- },
                onIncrement = { repeticiones++ }
            )

            Spacer(modifier = Modifier.height(32.dp))

            GradientButton(
                text = if (isEditing) "Guardar cambios" else "Agregar a la rutina",
                onClick = { onConfirm(series, repeticiones) },
                contentDesc = if (isEditing) {
                    "Guardar cambios de ${exercise.nombre}"
                } else {
                    "Agregar ${exercise.nombre} a la rutina"
                }
            )

            if (isEditing) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Cancelar")
                }
            }
        }
    }
}

@Composable
fun StepperRow(
    label: String,
    value: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        IconButton(
            onClick = onDecrement,
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = CircleShape
                )
                .border(
                    width = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
                .semantics { contentDescription = "Restar $label" }
        ) {
            Icon(
                imageVector = Icons.Filled.Remove,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }

        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(64.dp)
        )

        IconButton(
            onClick = onIncrement,
            modifier = Modifier
                .size(44.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape
                )
                .semantics { contentDescription = "Sumar $label" }
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

// Colapsable para no sobrecargar el formulario: colapsado muestra solo el resumen.
@Composable
fun DaySelectorSection(
    diasSeleccionados: Set<DiaSemana>,
    onToggleDia: (DiaSemana) -> Unit,
    onSetTodosLosDias: (Boolean) -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val todosSeleccionados = diasSeleccionados.size == DiaSemana.entries.size

    val resumen = when {
        diasSeleccionados.isEmpty() -> "Sin días fijos"
        todosSeleccionados -> "Todos los días"
        else -> diasSeleccionados.sortedBy { it.ordinal }.joinToString(" · ") { it.etiqueta }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .animateContentSize()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { expanded = !expanded }
                .padding(16.dp)
                .semantics {
                    contentDescription =
                        "Días de la semana: $resumen. " + if (expanded) "Contraer" else "Expandir"
                }
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DÍAS DE LA SEMANA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = resumen,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (expanded) {
            DayCheckRow(
                label = "Todos los días",
                checked = todosSeleccionados,
                onCheckedChange = { onSetTodosLosDias(it) },
                emphasized = true
            )
            DiaSemana.entries.forEach { dia ->
                DayCheckRow(
                    label = dia.nombre,
                    checked = dia in diasSeleccionados,
                    onCheckedChange = { onToggleDia(dia) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DayCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    emphasized: Boolean = false
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(role = Role.Checkbox) { onCheckedChange(!checked) }
            .padding(horizontal = 8.dp)
            .semantics(mergeDescendants = true) {}
    ) {
        Checkbox(
            checked = checked,
            // El toggle lo maneja la fila entera para dar un target táctil grande.
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Normal
        )
    }
}
