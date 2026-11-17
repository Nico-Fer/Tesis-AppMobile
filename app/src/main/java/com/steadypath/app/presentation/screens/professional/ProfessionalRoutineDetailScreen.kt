package com.steadypath.app.presentation.screens.professional

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.ExerciseCompliance
import com.steadypath.app.domain.model.RoutineExercise
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.GradientButton
import com.steadypath.app.presentation.screens.routines.resumenDias

@Composable
fun ProfessionalRoutineDetailScreen(
    routineId: String,
    onEditClick: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ProfessionalRoutineDetailViewModel = hiltViewModel()
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
            title = "Detalle de rutina",
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        when (val state = uiState) {
            is UiState.Loading -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            is UiState.Error -> Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }

            is UiState.Success -> RoutineDetailContent(
                data = state.data,
                onEditClick = onEditClick,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RoutineDetailContent(
    data: ProfessionalRoutineDetailData,
    onEditClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val routine = data.routine
    val diasLabel = routine.diasAsignados.resumenDias() ?: "Sin días fijos"
    val complianceByExercise = data.compliance?.ejercicios.orEmpty().associateBy { it.idEjercicio }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            text = routine.nombreRutina,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "$diasLabel · ${if (routine.activa) "Activa" else "Inactiva"}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        data.compliance?.let { compliance ->
            Text(
                text = "ADHERENCIA AL PLAN",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${compliance.porcentajeCumplimiento.toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(
            text = "Ejercicios",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        routine.ejercicios.forEach { ejercicio ->
            ExerciseRow(
                ejercicio = ejercicio,
                compliance = complianceByExercise[ejercicio.idEjercicio]
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        GradientButton(
            text = "Editar rutina",
            onClick = onEditClick,
            contentDesc = "Editar esta rutina"
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun ExerciseRow(
    ejercicio: RoutineExercise,
    compliance: ExerciseCompliance?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(16.dp)
    ) {
        Text(
            text = ejercicio.nombreEjercicio,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "${ejercicio.series} series · ${ejercicio.repeticionesObjetivo} repeticiones" +
                if (compliance != null) " · ${compliance.porcentajeCumplimiento.toInt()}% cumplido" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
