package com.steadypath.app.presentation.screens.realtime

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.steadypath.app.domain.model.CameraLens
import com.steadypath.app.domain.model.KeypointType
import com.steadypath.app.domain.model.PoseFrame

/**
 * Cámara en vivo con el esqueleto superpuesto.
 *
 * Existe para que **el lente y el espejado no puedan quedar desincronizados**. Antes cada
 * pantalla llamaba a [CameraPreview] y a [PoseOverlay] por separado; con dos lentes
 * posibles, pasarle el lente frontal a una y olvidarse del espejado en la otra dibujaría el
 * esqueleto del lado opuesto al cuerpo, y compilaría igual. Acá el espejado se deriva del
 * mismo [lens] que abre la cámara, de modo que el error deja de ser expresable.
 *
 * El espejado corresponde al lente frontal porque `PreviewView` espeja su imagen ---para
 * que la persona se vea como en un espejo--- mientras que el frame que llega al análisis no
 * está espejado. Ver [PoseOverlay] para el detalle del dibujo y [CameraLens] para la
 * advertencia sobre el signo de los ángulos, que es lo que hay que verificar en dispositivo
 * antes de dar por buena la intercambiabilidad de referencias entre lentes.
 *
 * Si el dispositivo no tiene el lente pedido, [CameraPreview] abre el otro: el espejado se
 * deriva del lente **efectivo**, no del pedido, para que la invariante se sostenga también
 * en ese caso.
 */
@Composable
fun PoseCameraView(
    lens: CameraLens,
    onFrame: (ImageProxy) -> Unit,
    pose: () -> PoseFrame?,
    modifier: Modifier = Modifier,
    onCameraReady: () -> Unit = {},
    onCameraError: (Throwable) -> Unit = {},
    highlightedKeypoint: KeypointType? = null,
    keypointColor: Color = Color(0xFF00E5FF),
    boneColor: Color = Color(0xFF0044A1)
) {
    var lenteEfectivo by remember(lens) { mutableStateOf(lens) }
    Box(modifier = modifier) {
        CameraPreview(
            onFrame = onFrame,
            modifier = Modifier.fillMaxSize(),
            lensFacing = lens.toCameraSelector(),
            onCameraReady = onCameraReady,
            onLensResolved = { lenteEfectivo = it.toCameraLens() },
            onCameraError = onCameraError
        )
        PoseOverlay(
            pose = pose,
            modifier = Modifier.fillMaxSize(),
            keypointColor = keypointColor,
            boneColor = boneColor,
            highlightedKeypoint = highlightedKeypoint,
            mirrored = lenteEfectivo.espejaElPreview()
        )
    }
}

/**
 * Igual que la sobrecarga anterior, pero resolviendo el lente por su cuenta a partir de la
 * elección del flujo y la preferencia del rol. Es la forma que usan las pantallas que no
 * tienen nada particular que decidir sobre la cámara.
 *
 * No abre la cámara hasta que el lente está resuelto: abrir con uno provisorio y cambiar
 * un instante después fuerza un rebind, y en el emulador (ambos lentes sobre la misma
 * webcam) ese cambio rápido deja el HAL de cámara inutilizable.
 */
@Composable
fun PoseCameraView(
    onFrame: (ImageProxy) -> Unit,
    pose: () -> PoseFrame?,
    modifier: Modifier = Modifier,
    onCameraReady: () -> Unit = {},
    onCameraError: (Throwable) -> Unit = {},
    highlightedKeypoint: KeypointType? = null,
    viewModel: CameraLensViewModel = hiltViewModel()
) {
    val lente by viewModel.lente.collectAsStateWithLifecycle()
    val resuelto = lente ?: return
    PoseCameraView(
        lens = resuelto,
        onFrame = onFrame,
        pose = pose,
        modifier = modifier,
        onCameraReady = onCameraReady,
        onCameraError = onCameraError,
        highlightedKeypoint = highlightedKeypoint
    )
}

/**
 * Traducción del modelo de dominio a la constante de CameraX. Vive en la capa de
 * presentación a propósito: [CameraLens] no debe conocer tipos de Android.
 */
fun CameraLens.toCameraSelector(): Int = when (this) {
    CameraLens.FRONTAL -> CameraSelector.LENS_FACING_FRONT
    CameraLens.TRASERA -> CameraSelector.LENS_FACING_BACK
}

/** Inversa de [toCameraSelector], para el lente que CameraX terminó abriendo. */
fun Int.toCameraLens(): CameraLens =
    if (this == CameraSelector.LENS_FACING_FRONT) CameraLens.FRONTAL else CameraLens.TRASERA

/**
 * Si el preview de este lente llega espejado respecto del frame de análisis.
 *
 * Único punto donde se decide: si la verificación en dispositivo mostrara que `PreviewView`
 * no se comporta como se espera, se corrige acá y no en cada pantalla.
 */
fun CameraLens.espejaElPreview(): Boolean = this == CameraLens.FRONTAL
