package com.steadypath.app.presentation.screens.calibration

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.data.vision.EstadoEstimador
import com.steadypath.app.domain.model.CameraLens
import com.steadypath.app.presentation.screens.realtime.CameraLensViewModel
import com.steadypath.app.presentation.screens.realtime.PoseCameraView
import com.steadypath.app.presentation.screens.realtime.rememberFeedbackEffects
import com.steadypath.app.presentation.components.CameraStatusOverlay
import com.steadypath.app.presentation.components.DomiTopBar
import com.steadypath.app.presentation.components.GradientButton
import kotlinx.coroutines.delay

/** Cuánto deben sostenerse los checks con cámara frontal antes de arrancar solo. */
private const val ESPERA_AUTO_INICIO_MS = 2_000L

/** Silencio mínimo entre dos avisos hablados de auto-inicio. */
private const val SILENCIO_ENTRE_AVISOS_MS = 5_000L

@Composable
fun PrepareSpaceScreen(
    exerciseId: String,
    exerciseName: String,
    onStartExercise: (String, String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: PrepareSpaceViewModel = hiltViewModel(),
    cameraViewModel: CameraLensViewModel = hiltViewModel()
) {
    val lenteElegido by cameraViewModel.lente.collectAsStateWithLifecycle()
    val pose by viewModel.pose.collectAsStateWithLifecycle()
    val cuerpoVisible by viewModel.cuerpoVisible.collectAsStateWithLifecycle()
    val buenaIluminacion by viewModel.buenaIluminacion.collectAsStateWithLifecycle()
    val camaraEstable by viewModel.camaraEstable.collectAsStateWithLifecycle()
    val listoParaEmpezar by viewModel.listoParaEmpezar.collectAsStateWithLifecycle()
    val estadoModelo by viewModel.estadoModelo.collectAsStateWithLifecycle()
    var showHelp by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<Throwable?>(null) }

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

    LaunchedEffect(exerciseId) { viewModel.load(exerciseId) }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Guarda contra la doble navegación: el auto-inicio y el botón pueden coincidir, y la
    // pantalla sigue compuesta durante la transición de salida.
    var navegando by remember { mutableStateOf(false) }
    val comenzar = {
        if (!navegando) {
            navegando = true
            onStartExercise(exerciseId, exerciseName)
        }
    }

    val puedeComenzar = listoParaEmpezar && cameraError == null &&
        estadoModelo !is EstadoEstimador.Error

    // Con la cámara frontal el paciente se ubicó lejos del teléfono para entrar en cuadro:
    // volver a tocar el botón lo saca del encuadre que acaba de lograr. Arranca solo cuando
    // los checks se sostienen [ESPERA_AUTO_INICIO_MS] seguidos, no al primer frame bueno,
    // porque "cuerpo visible" parpadea mientras la persona termina de acomodarse. Exigir
    // cameraReady evita disparar con checks viejos al volver desde la cuenta regresiva.
    val autoInicioArmado = lenteElegido == CameraLens.FRONTAL && cameraReady &&
        puedeComenzar && !showHelp && !navegando

    // Aviso hablado: el paciente está lejos y no necesariamente mirando la pantalla. Corto a
    // propósito: tiene que terminar antes de navegar, porque al salir de la pantalla se
    // libera el TTS y lo corta. El silencio mínimo evita repetirlo en cada parpadeo de
    // "cuerpo visible" mientras la persona se acomoda.
    val feedback = rememberFeedbackEffects()
    var ultimoAvisoMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(autoInicioArmado) {
        if (autoInicioArmado) {
            val ahora = SystemClock.elapsedRealtime()
            if (ahora - ultimoAvisoMs >= SILENCIO_ENTRE_AVISOS_MS) {
                ultimoAvisoMs = ahora
                feedback.speakAnnouncement("¡Perfecto! Ya empezamos.")
            }
            delay(ESPERA_AUTO_INICIO_MS)
            comenzar()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        DomiTopBar(title = "Prepará tu espacio", showBackArrow = true, onBackClick = onNavigateBack)

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ubicate frente a la cámara para el ejercicio $exerciseName",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (lenteElegido == CameraLens.TRASERA) {
                    "Pedile a alguien que sostenga el teléfono y asegurate de que se " +
                        "vea todo tu cuerpo."
                } else {
                    "Apoyá el teléfono de forma que te veas entero en la pantalla. Cuando " +
                        "se cumplan todos los puntos, el ejercicio arranca solo."
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(40.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(
                        width = 3.dp,
                        color = if (listoParaEmpezar) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)
                        },
                        shape = RoundedCornerShape(40.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (hasCameraPermission) {
                    val lente = lenteElegido
                    if (lente != null) {
                        PoseCameraView(
                            lens = lente,
                            onFrame = viewModel::onFrame,
                            pose = { pose },
                            onCameraReady = { cameraReady = true },
                            onCameraError = { cameraError = it },
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(40.dp))
                        )
                    }
                    CameraStatusOverlay(
                        cameraReady = cameraReady,
                        cameraError = cameraError,
                        estadoModelo = estadoModelo
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f),
                            modifier = Modifier.height(64.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }
                        ) {
                            Text("Dar permiso de cámara")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (listoParaEmpezar) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val onBadgeColor = if (listoParaEmpezar) {
                    MaterialTheme.colorScheme.onSecondary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = onBadgeColor,
                    modifier = Modifier.height(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        autoInicioArmado -> "¡PERFECTO! QUEDATE ASÍ…"
                        listoParaEmpezar -> "¡PERFECTO! YA PODÉS EMPEZAR"
                        else -> "PREPARÁNDOTE…"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = onBadgeColor
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            listOf(
                "Cuerpo completo visible" to cuerpoVisible,
                "Buena iluminación" to buenaIluminacion,
                "Cámara estable" to camaraEstable
            ).forEach { (item, cumplido) ->
                Row(
                    modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = if (cumplido) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            GradientButton(
                text = "Comenzar ejercicio →",
                onClick = comenzar,
                enabled = puedeComenzar,
                contentDesc = "Comenzar ejercicio $exerciseName"
            )
            TextButton(onClick = { showHelp = true }) {
                Text(
                    text = "Necesito ayuda",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) { Text("Entendido") }
            },
            title = { Text("Necesitás ayuda") },
            text = {
                Text(
                    "Asegurate de que todo tu cuerpo entre en el cuadro de la cámara, que " +
                        "haya buena luz y que el celular quede quieto y estable. Si el " +
                        "esqueleto no se dibuja bien, alejate un poco de la cámara."
                )
            }
        )
    }
}
