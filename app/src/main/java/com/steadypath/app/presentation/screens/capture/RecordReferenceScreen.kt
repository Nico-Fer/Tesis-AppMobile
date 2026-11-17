package com.steadypath.app.presentation.screens.capture

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.steadypath.app.domain.comparison.MovementPatternCatalog
import com.steadypath.app.presentation.components.CameraLensPicker
import com.steadypath.app.presentation.components.CameraStatusOverlay
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.GradientButton
import com.steadypath.app.presentation.screens.realtime.CameraLensViewModel
import com.steadypath.app.presentation.screens.realtime.PoseCameraView
import com.steadypath.app.presentation.screens.realtime.rememberFeedbackEffects

/**
 * Grabación de referencia DTW en producción: quien graba (paciente o profesional) ejecuta
 * [ReferenceCaptureViewModel.TARGET_REPS] repeticiones frente a la cámara; la app segmenta,
 * al completar el objetivo elige sola la mejor (medoide DTW, descartando outliers) y la sube
 * al backend asociada a (paciente, ejercicio). No hay paso manual de selección.
 *
 * Misma composición que RealtimeExerciseScreen (visor 3:4, badge de estado, contador) para
 * que la persona vea su cuerpo entero igual que cuando hace el ejercicio.
 *
 * [patientId] no nulo = graba el profesional para ese paciente; nulo = referencia propia.
 * Misma [ReferenceCaptureViewModel] que la herramienta debug: mismo pipeline por construcción.
 *
 * El lente se elige acá mismo, antes de «Comenzar»: el paciente llega desde el armado de la
 * rutina, donde no hay selector, y siempre abría con su predeterminado (frontal). La elección
 * es puntual del flujo y no toca la preferencia guardada; la limpia el ViewModel al salir.
 */
@Composable
fun RecordReferenceScreen(
    exerciseId: String,
    exerciseName: String,
    patternSlug: String,
    patientId: String?,
    onNavigateBack: () -> Unit,
    viewModel: ReferenceCaptureViewModel = hiltViewModel(),
    cameraViewModel: CameraLensViewModel = hiltViewModel()
) {
    val lenteElegido by cameraViewModel.lente.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Sin `by`: la pose se lee sólo dentro del Canvas del overlay (ver RealtimeExerciseScreen).
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
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        viewModel.configure(exerciseId, exerciseName, patternSlug, patientId)
        if (hasCameraPermission) viewModel.initialize()
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(hasCameraPermission) {
        if (hasCameraPermission) viewModel.initialize()
    }

    // Quien graba suele estar lejos del teléfono: el conteo y los avisos se dicen en voz alta.
    val feedback = rememberFeedbackEffects()
    LaunchedEffect(Unit) {
        viewModel.announcements.collect(feedback::speakAnnouncement)
    }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Mismo visor que RealtimeExerciseScreen: 3:4 a todo el ancho, la pantalla
            // scrollea si hace falta. Un recuadro chico no deja ver si entra el cuerpo entero.
            // Atados al lente: al cambiarlo el overlay vuelve a mostrar la carga durante el
            // rebind, y un error del lente anterior no queda pegado.
            var cameraReady by remember(lenteElegido) { mutableStateOf(false) }
            var cameraError by remember(lenteElegido) { mutableStateOf<Throwable?>(null) }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                    .semantics { contentDescription = "Visor de cámara para grabar la referencia" }
            ) {
                val lente = lenteElegido
                if (lente != null) {
                    PoseCameraView(
                        lens = lente,
                        onFrame = viewModel::onFrame,
                        pose = { poseState.value },
                        onCameraReady = { cameraReady = true },
                        onCameraError = { cameraError = it },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                CameraStatusOverlay(
                    cameraReady = cameraReady,
                    cameraError = cameraError,
                    estadoModelo = estadoModelo
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            RecordingStatusBadge(uiState)

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = exerciseName.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.reps.size}",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics {
                        contentDescription = "${uiState.reps.size} de " +
                            "${ReferenceCaptureViewModel.TARGET_REPS} repeticiones"
                    }
                )
                Text(
                    text = " / ${ReferenceCaptureViewModel.TARGET_REPS}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val pattern = MovementPatternCatalog.bySlug(patternSlug)
            val message = when {
                uiState.statusMessage.isNotEmpty() -> uiState.statusMessage
                uiState.isRecording -> "Terminá cada repetición en reposo."
                else -> buildString {
                    append(
                        "Hacé ${ReferenceCaptureViewModel.TARGET_REPS} repeticiones completas; " +
                            "la app elige la mejor y la guarda sola."
                    )
                    pattern?.notes?.takeIf { it.isNotBlank() }?.let { append("\n").append(it) }
                    if (patientId == null) {
                        append("\nSi podés, grabala con tu profesional al lado.")
                    }
                }
            }
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    uiState.uploadSuccess -> MaterialTheme.colorScheme.secondary
                    uiState.uploadFailed -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Oculto durante la toma: cambiar de lente a mitad de las repeticiones mezclaría
            // ejecuciones vistas desde lados distintos.
            if (!uiState.isRecording && !uiState.isUploading && !uiState.uploadSuccess) {
                CameraLensPicker(
                    descripcion = if (patientId == null) {
                        "La frontal te deja verte mientras grabás. Usá la trasera si alguien " +
                            "sostiene el teléfono."
                    } else {
                        "Usá la trasera si sostenés el teléfono. Elegí la frontal si lo vas " +
                            "a apoyar para que la persona se grabe sola."
                    }
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            when {
                uiState.uploadSuccess -> GradientButton(
                    text = "Listo",
                    onClick = onNavigateBack,
                    contentDesc = "Volver a la pantalla anterior"
                )
                uiState.isUploading -> GradientButton(
                    text = "Guardando…",
                    onClick = {},
                    enabled = false,
                    isLoading = true
                )
                uiState.uploadFailed -> {
                    GradientButton(
                        text = "Reintentar",
                        onClick = viewModel::saveToBackend,
                        contentDesc = "Reintentar guardar la referencia"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = viewModel::startRecording,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grabar de nuevo")
                    }
                }
                uiState.isRecording -> OutlinedButton(
                    onClick = viewModel::finishRecording,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Terminar")
                }
                else -> GradientButton(
                    text = "Comenzar",
                    onClick = viewModel::startRecording,
                    enabled = pattern != null,
                    contentDesc = "Comenzar a grabar la referencia"
                )
            }
        }
    }
}

@Composable
private fun RecordingStatusBadge(state: ReferenceCaptureUiState) {
    val (icon, label, containerColor, contentColor) = when {
        state.uploadSuccess -> Quadruple(
            Icons.Filled.CheckCircle,
            "Referencia guardada",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        state.uploadFailed -> Quadruple(
            Icons.Filled.Warning,
            "No se pudo guardar",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        state.isUploading -> Quadruple(
            null,
            "Eligiendo la mejor…",
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
        state.isRecording -> Quadruple(
            Icons.Filled.FiberManualRecord,
            "Grabando",
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer
        )
        else -> Quadruple(
            null,
            "Listo para grabar",
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

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
