package com.steadypath.app.presentation.screens.professional

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.presentation.common.UiState
import com.steadypath.app.presentation.components.DomiTextField
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.GradientButton

@Composable
fun AdjustToleranceScreen(
    patientId: String,
    exerciseId: String,
    exerciseName: String,
    onToleranceSaved: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: AdjustToleranceViewModel = hiltViewModel()
) {
    val anguloMinimo by viewModel.anguloMinimo.collectAsStateWithLifecycle()
    val anguloMaximo by viewModel.anguloMaximo.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val toleranceSaved by viewModel.toleranceSaved.collectAsStateWithLifecycle()

    LaunchedEffect(toleranceSaved) {
        if (toleranceSaved) onToleranceSaved()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            title = "Ajustar tolerancia",
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = exerciseName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Ángulos tolerados para este ejercicio, en grados (0-360).",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            DomiTextField(
                value = anguloMinimo,
                onValueChange = viewModel::onAnguloMinimoChange,
                label = "ÁNGULO MÍNIMO",
                placeholder = "Ej: 10",
                keyboardType = KeyboardType.Number
            )

            Spacer(modifier = Modifier.height(16.dp))

            DomiTextField(
                value = anguloMaximo,
                onValueChange = viewModel::onAnguloMaximoChange,
                label = "ÁNGULO MÁXIMO",
                placeholder = "Ej: 90",
                keyboardType = KeyboardType.Number
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
                text = "Guardar",
                onClick = { viewModel.saveTolerance(patientId, exerciseId) },
                isLoading = saveState is UiState.Loading,
                contentDesc = "Guardar tolerancia de $exerciseName"
            )
        }
    }
}
