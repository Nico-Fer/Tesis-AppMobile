package com.steadypath.app.presentation.screens.exercises

import androidx.compose.foundation.background
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color.Companion.Transparent
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.steadypath.app.domain.model.Exercise
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.BodyZonesDiagram
import com.steadypath.app.presentation.components.CameraLensPicker
import com.steadypath.app.presentation.components.GradientButton
import com.steadypath.app.presentation.components.DomiTopBar

@Composable
fun ExerciseDetailScreen(
    exerciseId: String,
    onStartExercise: (String, String) -> Unit,
    onRecordReference: (exerciseId: String, exerciseName: String, patternSlug: String) -> Unit,
    onNavigateBack: () -> Unit,
    onOpenPrivacy: () -> Unit = {},
    viewModel: ExerciseDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val videosState by viewModel.videosState.collectAsStateWithLifecycle()
    val startGate by viewModel.startGate.collectAsStateWithLifecycle()

    LaunchedEffect(exerciseId) {
        viewModel.loadExercise(exerciseId)
    }

    when (val state = uiState) {
        is UiState.Loading -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        is UiState.Error -> {
            Box(
                modifier = Modifier.fillMaxSize(),
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
            val data = state.data as ExerciseDetailData
            val exercise = data.exercise

            LaunchedEffect(startGate) {
                if (startGate == StartGate.Proceed) {
                    viewModel.consumeStartGate()
                    onStartExercise(exercise.idEjercicio, exercise.nombre)
                }
            }

            ExerciseDetailContent(
                data = data,
                videosState = videosState,
                isCheckingReference = startGate == StartGate.Checking,
                onStartExercise = { viewModel.onStartExercise(exercise) },
                onNavigateBack = onNavigateBack
            )

            if (startGate == StartGate.MissingReference) {
                MissingReferenceDialog(
                    onRecord = {
                        viewModel.consumeStartGate()
                        onRecordReference(
                            exercise.idEjercicio,
                            exercise.nombre,
                            exercise.patronMovimiento.orEmpty()
                        )
                    },
                    onDismiss = viewModel::consumeStartGate
                )
            }

            if (startGate == StartGate.ConsentRevoked) {
                AlertDialog(
                    onDismissRequest = viewModel::consumeStartGate,
                    title = { Text("Revocaste tu consentimiento") },
                    text = {
                        Text(
                            "Mientras no lo vuelvas a dar, la app no registra sesiones. " +
                                "Lo podés dar de nuevo en Privacidad y datos.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.consumeStartGate()
                            onOpenPrivacy()
                        }) { Text("Ir a Privacidad") }
                    },
                    dismissButton = {
                        TextButton(onClick = viewModel::consumeStartGate) { Text("Cerrar") }
                    }
                )
            }
        }
    }
}

/**
 * Sin referencia no se arranca: la comparación en vivo necesita el movimiento de esta
 * persona para decir algo. Se sigue advirtiendo qué se pierde al grabarla sin profesional.
 */
@Composable
private fun MissingReferenceDialog(onRecord: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Primero grabá tu referencia") },
        text = {
            Text(
                "Aún no tenés una referencia para este ejercicio. Es el movimiento " +
                    "con el que la app va a comparar todas tus repeticiones, así que hay que " +
                    "grabarla antes de empezar.\n\n" +
                    "Si la grabás con tu kinesiólogo o profesor al lado, la app va a poder " +
                    "decirte si estás haciendo bien el ejercicio. Si la grabás solo, va a " +
                    "poder avisarte si te apartás de ella, pero no si está bien hecha.",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            TextButton(onClick = onRecord) { Text("Grabar referencia") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ahora no") }
        }
    )
}

@Composable
private fun ExerciseDetailContent(
    data: ExerciseDetailData,
    videosState: UiState<List<com.steadypath.app.domain.model.ExerciseVideo>>,
    isCheckingReference: Boolean,
    onStartExercise: () -> Unit,
    onNavigateBack: () -> Unit
) {
    val exercise = data.exercise
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            title = exercise.nombre,
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        // El video se desplaza junto con el contenido. Fijo arriba, con la letra del sistema
        // agrandada dejaba tan poco lugar en el medio que la descripción y el selector de
        // cámara parecían pasar por debajo de él, como un pop-up.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
        ExerciseVideoSection(
            videosState = videosState,
            exerciseName = exercise.nombre
        )

        // Content
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Text(
                text = exercise.nombre,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTab < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Descripción",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Zonas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (selectedTab) {
                0 -> DescriptionTab(data.formattedSteps)
                1 -> ZonesTab(exercise)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pagination dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(2) { index ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == selectedTab) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                    )
                    if (index < 1) Spacer(modifier = Modifier.width(8.dp))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dentro del scroll a propósito: fijo abajo de la pantalla tapaba la descripción
            // y las zonas en pantallas chicas, y no se podía bajar para verlo.
            CameraLensPicker(
                descripcion = "La frontal te deja ver la pantalla mientras hacés el " +
                    "ejercicio. Usá la trasera si alguien sostiene el teléfono."
            )
        }
        }

        // Bottom CTA
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            GradientButton(
                text = "Comenzar ▶",
                onClick = onStartExercise,
                isLoading = isCheckingReference,
                contentDesc = "Comenzar ejercicio ${exercise.nombre}"
            )
        }
    }
}

@Composable
private fun DescriptionTab(formattedSteps: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(20.dp)
    ) {
        formattedSteps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ZonesTab(exercise: Exercise) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(20.dp)
    ) {
        Text(
            text = "Zonas que trabajás",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Diagrama anatómico con las zonas del ejercicio resaltadas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .semantics {
                    contentDescription = "Ilustración anatómica. Zonas afectadas: " +
                        exercise.zonasAfectadas.joinToString(", ")
                },
            contentAlignment = Alignment.Center
        ) {
            BodyZonesDiagram(
                highlightedZones = exercise.zonasAfectadas,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = if (exercise.zonasAfectadas.isEmpty()) {
                "Este ejercicio no tiene zonas registradas."
            } else {
                "Este ejercicio involucra principalmente los músculos de " +
                    "${exercise.zonasAfectadas.joinToString(" y ")}."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
