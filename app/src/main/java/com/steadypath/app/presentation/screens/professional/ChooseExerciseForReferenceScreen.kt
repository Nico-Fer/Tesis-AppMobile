package com.steadypath.app.presentation.screens.professional

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import com.steadypath.app.presentation.components.CameraLensPicker
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.CatalogExerciseCard
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.screens.exercises.ExerciseListViewModel

/**
 * El profesional elige para qué ejercicio grabar la referencia del paciente. Solo se
 * listan ejercicios con patrón de movimiento declarado (sin patrón no se sabe qué
 * ángulos medir); reutiliza [ExerciseListViewModel] (catálogo completo, filtrado acá).
 */
@Composable
fun ChooseExerciseForReferenceScreen(
    patientName: String,
    onExerciseSelected: (exerciseId: String, exerciseName: String, patternSlug: String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ExerciseListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            title = "Grabar referencia",
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Elegí el ejercicio",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            CameraLensPickerEnFlujo()

            Text(
                text = "La referencia grabada quedará asociada a $patientName.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            when (val state = uiState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                is UiState.Success<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val exercises = (state.data as List<Exercise>)
                        .filter { it.patronMovimiento != null }
                    if (exercises.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Ningún ejercicio permite grabar una referencia.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            items(exercises, key = { it.idEjercicio }) { exercise ->
                                CatalogExerciseCard(
                                    exercise = exercise,
                                    onClick = {
                                        onExerciseSelected(
                                            exercise.idEjercicio,
                                            exercise.nombre,
                                            exercise.patronMovimiento!!
                                        )
                                    }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                }
            }
        }
    }
}


/**
 * Selector de cámara del flujo de grabación del profesional. Se separa en su propio
 * componente para no cargar el cuerpo de la pantalla, que ya es largo.
 */
@Composable
private fun CameraLensPickerEnFlujo() {
    Spacer(modifier = Modifier.height(16.dp))
    CameraLensPicker(
        descripcion = "Usá la trasera si sostenés el teléfono. Elegí la frontal si lo vas " +
            "a apoyar para que la persona se grabe sola."
    )
    Spacer(modifier = Modifier.height(20.dp))
}
