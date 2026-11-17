package com.steadypath.app.presentation.screens.pain

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.BottomNavTab
import com.steadypath.app.presentation.components.GradientButton
import com.steadypath.app.presentation.components.DomiBottomNavBar
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.ui.theme.Error
import com.steadypath.app.presentation.ui.theme.Secondary
import com.steadypath.app.presentation.ui.theme.Tertiary

@Composable
fun PainReportScreen(
    exerciseId: String,
    exerciseName: String,
    onNextExercise: (exerciseId: String) -> Unit,
    onFinish: (durationMinutes: Int) -> Unit,
    viewModel: PainReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var painValue by remember { mutableFloatStateOf(0f) }
    val context = LocalContext.current

    // Toast y no snackbar: el aviso debe sobrevivir a la navegación inmediata.
    LaunchedEffect(Unit) {
        viewModel.userMessage.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { event ->
            when (event) {
                is PainReportViewModel.NavigationEvent.NextExercise ->
                    onNextExercise(event.exerciseId)
                is PainReportViewModel.NavigationEvent.RoutineFinished ->
                    onFinish(event.durationMinutes)
            }
        }
    }

    val painLevel = painValue.toInt()
    val painData = getPainData(painLevel)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            title = "Reportar Dolor",
            showBackArrow = true,
            onBackClick = { viewModel.skip() }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Exercise info
            Text(
                text = "EJERCICIO FINALIZADO",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(40.dp))

            // Large emoji
            Text(
                text = painData.emoji,
                fontSize = 72.sp,
                modifier = Modifier.semantics {
                    contentDescription = "Indicador de dolor nivel $painLevel"
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Pain number
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "$painLevel",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = painData.color
                )
                Text(
                    text = " / 10",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pain label
            Text(
                text = painData.label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = painData.color
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Slider
            Slider(
                value = painValue,
                onValueChange = { painValue = it },
                valueRange = 0f..10f,
                steps = 9,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics {
                        contentDescription = "Escala de dolor del 0 al 10. Valor actual: $painLevel"
                    },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
            )

            // Scale markers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "5",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "10",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Confirm button
            GradientButton(
                text = "Confirmar",
                onClick = { viewModel.submitPainReport(painLevel) },
                isLoading = uiState is UiState.Loading,
                contentDesc = "Confirmar nivel de dolor $painLevel"
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Skip link
            TextButton(
                onClick = { viewModel.skip() },
                modifier = Modifier
                    .heightIn(min = 56.dp)
                    .semantics { contentDescription = "Saltar reporte de dolor" }
            ) {
                Text(
                    text = "Saltar por ahora",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        val noOpTabSelected = remember { { _: BottomNavTab -> } }
        DomiBottomNavBar(
            selectedTab = BottomNavTab.Routines,
            onTabSelected = noOpTabSelected
        )
    }
}

private data class PainData(
    val emoji: String,
    val label: String,
    val color: Color
)

private fun getPainData(level: Int): PainData = when (level) {
    0 -> PainData("😊", "SIN DOLOR", Secondary)
    1 -> PainData("🙂", "LEVE", Secondary)
    2 -> PainData("😐", "MOLESTIA", Secondary)
    3 -> PainData("🤨", "NOTABLE", Tertiary)
    4 -> PainData("😕", "INCÓMODO", Tertiary)
    5 -> PainData("😟", "MODERADO", Tertiary)
    6 -> PainData("😣", "FUERTE", Tertiary)
    7 -> PainData("😫", "MUY FUERTE", Error)
    8 -> PainData("😡", "INTENSO", Error)
    9 -> PainData("🤮", "INSOPORTABLE", Error)
    10 -> PainData("😭", "PEOR POSIBLE", Error)
    else -> PainData("😊", "SIN DOLOR", Secondary)
}
