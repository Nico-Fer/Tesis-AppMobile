package com.steadypath.app.presentation.screens.realtime

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors

/**
 * Vista de cámara con CameraX. Bindea un [Preview] (lo que ve el usuario) y un
 * [ImageAnalysis] (frames para inferencia) al ciclo de vida de la pantalla.
 *
 * El análisis corre sobre un executor de **un solo hilo** con
 * [ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST]: mientras [onFrame] procesa un frame, los
 * nuevos se descartan → backpressure por descarte sin colas manuales. [onFrame] recibe el
 * [ImageProxy] y es responsable de cerrarlo.
 *
 * [lensFacing] admite los dos lentes. El frontal es el predeterminado del paciente porque
 * deja la pantalla de cara a la persona, que así puede ver el esqueleto, el contador y el
 * botón de emergencia mientras ejercita; el trasero sirve cuando otra persona sostiene el
 * teléfono o hace falta más campo de visión.
 *
 * El preview del lente frontal llega espejado y el frame de análisis no, de modo que el
 * overlay debe corregirlo. **No llamar a este composable directamente**: usar
 * `PoseCameraView`, que deriva esa corrección del mismo lente y hace imposible que queden
 * desincronizados.
 *
 * Si el lente pedido no existe en el dispositivo se abre el otro y se avisa por
 * [onLensResolved] (el overlay espeja según el lente **efectivo**). Si no hay ninguna
 * cámara o el bind falla, se reporta por [onCameraError] en vez de dejar el visor negro.
 */
@Composable
fun CameraPreview(
    onFrame: (ImageProxy) -> Unit,
    modifier: Modifier = Modifier,
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    onCameraReady: () -> Unit = {},
    onLensResolved: (Int) -> Unit = {},
    onCameraError: (Throwable) -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    // Callbacks leídos por referencia actualizada: un lambda nuevo no debe rebindear la cámara.
    val currentOnFrame by rememberUpdatedState(onFrame)
    val currentOnCameraReady by rememberUpdatedState(onCameraReady)
    val currentOnLensResolved by rememberUpdatedState(onLensResolved)
    val currentOnCameraError by rememberUpdatedState(onCameraError)

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // TextureView en vez de SurfaceView: el preview se compone dentro de la
            // jerarquía de Compose, de modo que el clip de esquinas redondeadas y el
            // overlay del esqueleto se dibujan encima. Con PERFORMANCE (SurfaceView) la
            // superficie va en una ventana aparte y el fondo opaco del Box la tapa → negro.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    // Keyed por lente: cambiarlo desbindea los use cases anteriores y abre el nuevo.
    DisposableEffect(lensFacing, lifecycleOwner) {
        val session = CameraSessionHolder()

        // Avisa cuando el preview realmente empieza a emitir frames (no solo cuando se
        // bindeó la cámara): ese es el momento en que la imagen deja de estar en negro.
        val streamObserver = Observer<PreviewView.StreamState> { streamState ->
            if (streamState == PreviewView.StreamState.STREAMING) currentOnCameraReady()
        }
        previewView.previewStreamState.observe(lifecycleOwner, streamObserver)

        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (session.disposed) return@addListener
            val cameraProvider = providerFuture.get()
            session.provider = cameraProvider

            val resolvedLens = resolveAvailableLens(cameraProvider, lensFacing)
            if (resolvedLens == null) {
                val error = IllegalStateException("No se encontró ninguna cámara en el dispositivo.")
                Log.e(TAG, "Sin cámaras disponibles", error)
                currentOnCameraError(error)
                return@addListener
            }
            if (resolvedLens != lensFacing) {
                Log.w(TAG, "Lente $lensFacing no disponible; se usa $resolvedLens")
            }
            currentOnLensResolved(resolvedLens)

            // Mismo aspect ratio en ambos casos de uso ⇒ mismo campo de visión. El
            // overlay deriva su transformación del frame de análisis y el PreviewView
            // de su propio stream: si CameraX los resolviera a relaciones distintas, el
            // esqueleto quedaría corrido respecto del cuerpo. En 1.4.1 los dos ya usan
            // 4:3 por defecto, pero FALLBACK_RULE_AUTO puede separarlos en un
            // dispositivo sin 4:3 soportado; fijarlo lo vuelve determinista.
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()

            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.surfaceProvider = previewView.surfaceProvider }

            val imageAnalysis = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()
                .also { it.setAnalyzer(analysisExecutor) { image -> currentOnFrame(image) } }

            val selector = CameraSelector.Builder()
                .requireLensFacing(resolvedLens)
                .build()

            // Solo se desbindean los use cases propios: el provider es único por proceso y
            // un unbindAll() acá le quitaría la cámara a la pantalla siguiente si ya bindeó
            // durante la transición.
            session.useCases = arrayOf(preview, imageAnalysis)
            try {
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo bindear la cámara", e)
                currentOnCameraError(e)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            session.disposed = true
            previewView.previewStreamState.removeObserver(streamObserver)
            val provider = session.provider
            val useCases = session.useCases
            if (provider != null && useCases != null) {
                runCatching { provider.unbind(*useCases) }
            }
        }
    }

    AndroidView(modifier = modifier, factory = { previewView })
}

/** Lente pedido si existe; si no, el opuesto; `null` si el dispositivo no tiene cámara. */
private fun resolveAvailableLens(provider: ProcessCameraProvider, requested: Int): Int? {
    fun has(lens: Int) = runCatching {
        provider.hasCamera(CameraSelector.Builder().requireLensFacing(lens).build())
    }.getOrDefault(false)

    val fallback = if (requested == CameraSelector.LENS_FACING_FRONT) {
        CameraSelector.LENS_FACING_BACK
    } else {
        CameraSelector.LENS_FACING_FRONT
    }
    return when {
        has(requested) -> requested
        has(fallback) -> fallback
        else -> null
    }
}

private class CameraSessionHolder {
    var provider: ProcessCameraProvider? = null
    var useCases: Array<UseCase>? = null
    var disposed = false
}

private const val TAG = "CameraPreview"
