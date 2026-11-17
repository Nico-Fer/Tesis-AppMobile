package com.steadypath.app.presentation.screens.realtime

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.data.vision.EstadoEstimador
import com.steadypath.app.domain.model.PoseFrame
import com.steadypath.app.presentation.components.CameraStatusOverlay
import com.steadypath.app.presentation.components.EmergencyCallButton
import com.steadypath.app.presentation.components.GradientButton
import com.steadypath.app.presentation.components.DomiTopBar

@Composable
fun RealtimeExerciseScreen(
    exerciseId: String,
    exerciseName: String,
    onExerciseCompleted: (durationSeconds: Int) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: RealtimeExerciseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Sin `by`: la pose no se lee acá sino dentro del Canvas del overlay, para que un frame
    // nuevo invalide sólo el dibujo del esqueleto y no recomponga toda la pantalla.
    val poseState = viewModel.pose.collectAsStateWithLifecycle()
    val estadoModelo by viewModel.estadoModelo.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) viewModel.initialize(exerciseId, exerciseName) else viewModel.onPermissionDenied()
    }

    LaunchedEffect(exerciseId, exerciseName) {
        if (hasCameraPermission) {
            viewModel.initialize(exerciseId, exerciseName)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(
            title = exerciseName,
            showBackArrow = true,
            onBackClick = onNavigateBack
        )

        when (val state = uiState) {
            is RealtimeUiState.Idle -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is RealtimeUiState.CameraPermissionRequired -> {
                PermissionRequiredContent(
                    onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
            is RealtimeUiState.Active -> {
                ActiveExerciseContent(
                    state = state,
                    pose = { poseState.value },
                    estadoModelo = estadoModelo,
                    onFrame = viewModel::onFrame,
                    onStartSeries = viewModel::onStartSeries,
                    onSkipSeries = viewModel::onSkipSeries
                )

                // Refuerzo no visual por repetición: se dispara al cambiar el contador,
                // no en cada recomposición.
                val feedbackEffects = rememberFeedbackEffects()
                LaunchedEffect(state.repCount) {
                    // Al llegar al objetivo habla el anuncio de serie, no el feedback por rep.
                    if (state.repCount > 0 && state.repCount < state.targetReps) {
                        when (state.postureStatus) {
                            PostureStatus.NeedsCorrection -> {
                                feedbackEffects.vibrateCorrection()
                                feedbackEffects.speakCorrection(state.feedbackMessage)
                            }
                            PostureStatus.Good -> if (state.goodStreak >= 2) {
                                // Racha de reps correctas: se cuenta en voz alta.
                                feedbackEffects.speakRepCount(state.repCount)
                            } else {
                                feedbackEffects.speakEncouragement("¡Muy bien!")
                            }
                            PostureStatus.Unknown -> Unit
                        }
                    }
                }
                LaunchedEffect(state.seriesAnnouncement) {
                    state.seriesAnnouncement?.let { feedbackEffects.speakAnnouncement(it) }
                }
            }
            is RealtimeUiState.Completed -> {
                LaunchedEffect(state) {
                    onExerciseCompleted(state.durationSeconds)
                }
            }
            is RealtimeUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveExerciseContent(
    state: RealtimeUiState.Active,
    pose: () -> PoseFrame?,
    estadoModelo: EstadoEstimador,
    onFrame: (ImageProxy) -> Unit,
    onStartSeries: () -> Unit,
    onSkipSeries: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Visor de cámara con el esqueleto superpuesto. Tamaño fijo por aspect ratio (no
        // weight) para que ocupe la mayor parte posible de la pantalla sin depender de
        // cuánto contenido haya debajo; la pantalla entera scrollea si hace falta.
        var cameraReady by remember { mutableStateOf(false) }
        var cameraError by remember { mutableStateOf<Throwable?>(null) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .semantics { contentDescription = "Visor de cámara para análisis postural" }
        ) {
            PoseCameraView(
                onFrame = onFrame,
                pose = pose,
                onCameraReady = { cameraReady = true },
                onCameraError = { cameraError = it },
                modifier = Modifier.fillMaxSize()
            )
            CameraStatusOverlay(
                cameraReady = cameraReady,
                cameraError = cameraError,
                estadoModelo = estadoModelo
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Posture status indicator
        PostureStatusBadge(status = state.postureStatus)

        Spacer(modifier = Modifier.height(16.dp))

        // Serie actual dentro de la prescripción de la rutina
        Text(
            text = "SERIE ${state.currentSeries} DE ${state.totalSeries}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics {
                contentDescription = "Serie ${state.currentSeries} de ${state.totalSeries}"
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Rep counter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${state.repCount}",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics {
                    contentDescription = "${state.repCount} de ${state.targetReps} repeticiones"
                }
            )
            Text(
                text = " / ${state.targetReps}",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (state.feedbackMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = state.feedbackMessage,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // El cierre normal de la serie es automático al llegar al objetivo (ver
        // startComparison/AUTO_STOP_DELAY_MS en el ViewModel). Mientras se cuentan
        // repeticiones sólo se ofrece saltearla, como acción secundaria (outlined, no
        // gradiente) para no competir con la cámara; desaparece al alcanzar el objetivo
        // porque el cierre ya está en camino. "Comenzar" vuelve en el descanso entre series,
        // y el de emergencia queda siempre debajo.
        // En el descanso también se puede saltear la serie que sigue: en la prueba con
        // usuarios, una participante pidió «un botón para saltear la serie» porque no lo
        // encontró, ya que solo aparecía con la serie en curso.
        val puedeSaltear = !state.seriesInProgress || state.repCount < state.targetReps
        var confirmarSalteo by remember { mutableStateOf(false) }
        // Si la serie se cierra sola con el diálogo abierto, el diálogo se va con ella.
        LaunchedEffect(puedeSaltear) { if (!puedeSaltear) confirmarSalteo = false }

        if (!state.seriesInProgress) {
            GradientButton(
                text = "Comenzar",
                onClick = onStartSeries,
                contentDesc = "Comenzar la serie ${state.currentSeries} de ${state.totalSeries}"
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        if (puedeSaltear) {
            OutlinedButton(
                onClick = { confirmarSalteo = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics {
                        contentDescription =
                            "Saltear la serie ${state.currentSeries} de ${state.totalSeries}"
                    },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Saltear serie",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        EmergencyCallButton()

        if (confirmarSalteo) {
            SkipSeriesDialog(
                state = state,
                onConfirm = {
                    confirmarSalteo = false
                    onSkipSeries()
                },
                onDismiss = { confirmarSalteo = false }
            )
        }
    }
}

/**
 * Confirma el salteo mostrando cuánto se hizo de la serie. Las reps se leen del estado en
 * vivo: la cámara sigue contando mientras el diálogo está abierto.
 */
@Composable
private fun SkipSeriesDialog(
    state: RealtimeUiState.Active,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val esUltimaSerie = state.currentSeries >= state.totalSeries
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Saltear esta serie?") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // En el descanso la serie todavía no empezó: no hay repeticiones que mostrar.
                if (state.seriesInProgress) {
                    Text(
                        text = "Llevás",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${state.repCount} / ${state.targetReps}",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics {
                            contentDescription =
                                "${state.repCount} de ${state.targetReps} repeticiones"
                        }
                    )
                    Text(
                        text = "repeticiones",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = if (esUltimaSerie) {
                        "Es la última serie: el ejercicio va a terminar."
                    } else {
                        "Vas a pasar al descanso antes de la serie ${state.currentSeries + 1}."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Saltear serie") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Seguir") }
        }
    )
}

@Composable
private fun PostureStatusBadge(status: PostureStatus) {
    val (icon, label, containerColor, contentColor) = when (status) {
        PostureStatus.Good -> Quadruple(
            Icons.Filled.CheckCircle,
            "Postura correcta",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        PostureStatus.NeedsCorrection -> Quadruple(
            Icons.Filled.Warning,
            "Corregir postura",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        PostureStatus.Unknown -> Quadruple(
            null,
            "Analizando…",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = contentColor
        )
    }
}

@Composable
private fun PermissionRequiredContent(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📷",
            style = MaterialTheme.typography.displayLarge
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Falta el permiso de cámara",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Para analizar tu postura durante el ejercicio, la app necesita acceso a la cámara.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        GradientButton(
            text = "Conceder permiso",
            onClick = onRequestPermission,
            contentDesc = "Conceder permiso de cámara"
        )
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
