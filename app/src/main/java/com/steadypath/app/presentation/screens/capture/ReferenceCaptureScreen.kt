package com.steadypath.app.presentation.screens.capture

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.presentation.components.GradientButton
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.screens.realtime.PoseCameraView

/**
 * Pantalla DEBUG de captura de referencias: el fisioterapeuta elige un patrón del
 * catálogo, ejecuta N reps frente a la cámara y exporta el JSON v1 con el UUID real
 * del ejercicio. El archivo luego se copia a `app/src/main/assets/references/`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReferenceCaptureScreen(
    onNavigateBack: () -> Unit,
    viewModel: ReferenceCaptureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val poseState = viewModel.pose.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (hasCameraPermission) viewModel.initialize()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) viewModel.initialize()
    }

    // Al exportar, dispara el share intent con el JSON.
    LaunchedEffect(Unit) {
        viewModel.exportedFile.collect { file ->
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Compartir referencia"))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            title = "Captura de referencia (debug)",
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        if (!hasCameraPermission) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Se requiere permiso de cámara.",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            return@Column
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Visor con esqueleto
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                PoseCameraView(
                    onFrame = viewModel::onFrame,
                    pose = { poseState.value },
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selector de patrón del catálogo
            var patternMenuExpanded by remember { mutableStateOf(false) }
            val selectedPattern = uiState.patterns.first { it.slug == uiState.selectedPatternSlug }
            ExposedDropdownMenuBox(
                expanded = patternMenuExpanded,
                onExpandedChange = { if (!uiState.isRecording) patternMenuExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedPattern.displayName,
                    onValueChange = {},
                    readOnly = true,
                    enabled = !uiState.isRecording,
                    label = { Text("Patrón de movimiento") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = patternMenuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    supportingText = { Text(selectedPattern.notes) }
                )
                ExposedDropdownMenu(
                    expanded = patternMenuExpanded,
                    onDismissRequest = { patternMenuExpanded = false }
                ) {
                    uiState.patterns.forEach { pattern ->
                        DropdownMenuItem(
                            text = { Text(pattern.displayName) },
                            onClick = {
                                viewModel.onPatternSelected(pattern.slug)
                                patternMenuExpanded = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.exerciseId,
                onValueChange = viewModel::onExerciseIdChanged,
                label = { Text("UUID del ejercicio (backend)") },
                placeholder = { Text("Pegá el idEjercicio de GET /ejercicios") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (uiState.isRecording) viewModel.stopRecording()
                        else viewModel.startRecording()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (uiState.isRecording) "Detener" else "Grabar reps")
                }
                TextButton(
                    onClick = viewModel::clearReps,
                    enabled = uiState.reps.isNotEmpty() && !uiState.isRecording
                ) {
                    Text("Borrar")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Repeticiones detectadas: ${uiState.reps.size}",
                style = MaterialTheme.typography.titleMedium
            )
            uiState.reps.forEach { rep ->
                Text(
                    text = "Rep ${rep.index}: ${rep.frameCount} frames, ${rep.durationMs} ms",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.statusMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.statusMessage,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            GradientButton(
                text = "Elegir mediana y exportar",
                onClick = viewModel::export,
                enabled = uiState.reps.isNotEmpty() && !uiState.isRecording,
                contentDesc = "Exportar la referencia como JSON"
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
