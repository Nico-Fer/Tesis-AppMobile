package com.steadypath.app.presentation.screens.realtime

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.steadypath.app.domain.model.Keypoint
import com.steadypath.app.domain.model.KeypointType
import com.steadypath.app.domain.model.PoseFrame
import kotlin.math.max

/**
 * Dibuja el esqueleto de un [PoseFrame] sobre la vista de cámara.
 *
 * Los keypoints vienen normalizados respecto del frame original. Para que coincidan con un
 * [androidx.camera.view.PreviewView] en modo FILL_CENTER se aplica el mismo mapeo "cover":
 * se escala el frame para cubrir el lienzo y se centra, recortando el eje sobrante. Ambos
 * casos de uso de CameraX están fijados al mismo aspect ratio (ver `CameraPreview`), así que
 * comparten el campo de visión y las dos transformaciones coinciden.
 *
 * La pose se recibe como **lambda y no como valor** a propósito: así se lee dentro del
 * `Canvas`, que es una lectura de fase de dibujo. A ~20 fps eso invalida sólo el dibujo del
 * lienzo, sin recomponer la pantalla que lo contiene.
 *
 * [highlightedKeypoint] resalta una articulación puntual con un pulso animado — usado en la
 * calibración para señalar cuál es la que se está midiendo (p. ej. la rodilla en
 * "Extensión de rodilla").
 *
 * [mirrored] invierte el eje horizontal al proyectar. Hace falta con el lente frontal,
 * porque `PreviewView` espeja su imagen para que la persona se vea como en un espejo
 * mientras que el frame que llega al análisis **no** está espejado: sin esta corrección el
 * esqueleto aparecería del lado opuesto al cuerpo. Es una corrección de presentación y por
 * eso vive acá y no en el pipeline: los keypoints del dominio siguen normalizados respecto
 * del frame original, tal como los produce el postprocesador, y la comparación de movimiento
 * sigue leyendo esas coordenadas sin enterarse de esta corrección. Eso **no** significa que
 * el espejado sea inocuo para la comparación: los ángulos son signados y espejar los
 * invierte (ver `AngleExtractor`). Ver [com.steadypath.app.domain.model.CameraLens].
 *
 * En la práctica no debería fijarse a mano: usar `PoseCameraView`, que deriva este valor
 * del mismo lente con el que abre la cámara.
 */
@Composable
fun PoseOverlay(
    pose: () -> PoseFrame?,
    modifier: Modifier = Modifier,
    keypointColor: Color = Color(0xFF00E5FF),
    boneColor: Color = Color(0xFF0044A1),
    minConfidence: Float = MIN_CONFIDENCE,
    highlightedKeypoint: KeypointType? = null,
    highlightColor: Color = Color(0xFF852B00),
    mirrored: Boolean = false
) {
    val pulseScale = if (highlightedKeypoint != null) {
        val transition = rememberInfiniteTransition(label = "keypointPulse")
        val scale by transition.animateFloat(
            initialValue = 1f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "keypointPulseScale"
        )
        scale
    } else {
        1f
    }

    Canvas(modifier = modifier) {
        val frame = pose() ?: return@Canvas
        val srcW = frame.sourceWidth.toFloat()
        val srcH = frame.sourceHeight.toFloat()
        if (srcW <= 0f || srcH <= 0f) return@Canvas

        // Mapeo "cover" equivalente a PreviewView.ScaleType.FILL_CENTER.
        val scale = max(size.width / srcW, size.height / srcH)
        val offsetX = (size.width - srcW * scale) / 2f
        val offsetY = (size.height - srcH * scale) / 2f

        fun project(kp: Keypoint): Offset {
            val x = if (mirrored) 1f - kp.x else kp.x
            return Offset(
                x = offsetX + x * srcW * scale,
                y = offsetY + kp.y * srcH * scale
            )
        }

        // Las coordenadas ya no vienen clampeadas: una articulación fuera de cuadro cae
        // fuera del lienzo y se descarta, en vez de dibujarse pegada al borde.
        val margin = OUT_OF_BOUNDS_MARGIN.toPx()
        fun visible(p: Offset) =
            p.x >= -margin && p.y >= -margin &&
                p.x <= size.width + margin && p.y <= size.height + margin

        val haloExtra = HALO_EXTRA_WIDTH.toPx()
        val boneWidth = BONE_WIDTH.toPx()
        val haloWidth = boneWidth + haloExtra
        val jointRadius = JOINT_RADIUS.toPx()
        val kps = frame.keypoints

        // Se resuelven las posiciones una sola vez: cada articulación participa de varios
        // huesos y proyectar en cada tramo repetiría el cálculo.
        val points = arrayOfNulls<Offset>(kps.size)
        for (i in kps.indices) {
            val kp = kps[i]
            if (kp.confidence < minConfidence) continue
            val p = project(kp)
            if (visible(p)) points[i] = p
        }

        // Huesos en dos pasadas: primero todos los halos oscuros, después todos los trazos
        // de color. Al revés, el halo de un hueso taparía el trazo del hueso vecino.
        for (pass in 0..1) {
            for ((a, b) in SKELETON) {
                val pa = points.getOrNull(a) ?: continue
                val pb = points.getOrNull(b) ?: continue
                drawLine(
                    color = if (pass == 0) HALO_COLOR else boneColor,
                    start = pa,
                    end = pb,
                    strokeWidth = if (pass == 0) haloWidth else boneWidth,
                    cap = StrokeCap.Round
                )
            }
        }

        // Articulaciones, con el mismo halo para que se despeguen del fondo.
        for (p in points) {
            if (p == null) continue
            drawCircle(color = HALO_COLOR, radius = jointRadius + haloExtra, center = p)
            drawCircle(color = keypointColor, radius = jointRadius, center = p)
        }

        // Resalte de calibración: se dibuja último para quedar por encima del esqueleto.
        if (highlightedKeypoint != null) {
            val highlighted = points.getOrNull(highlightedKeypoint.ordinal)
            if (highlighted != null) {
                val pulseRadius = jointRadius * pulseScale
                drawCircle(
                    color = highlightColor.copy(alpha = 0.25f),
                    radius = pulseRadius + haloExtra * 2,
                    center = highlighted
                )
                drawCircle(color = HALO_COLOR, radius = pulseRadius + haloExtra, center = highlighted)
                drawCircle(color = highlightColor, radius = pulseRadius, center = highlighted)
            }
        }
    }
}

/**
 * Conexiones del esqueleto COCO-17 por índice ordinal de
 * [com.steadypath.app.domain.model.KeypointType].
 */
private val SKELETON = listOf(
    0 to 1, 0 to 2, 1 to 3, 2 to 4,        // cara
    5 to 6,                                  // hombros
    5 to 7, 7 to 9,                          // brazo izq
    6 to 8, 8 to 10,                         // brazo der
    5 to 11, 6 to 12,                        // torso
    11 to 12,                                // cadera
    11 to 13, 13 to 15,                      // pierna izq
    12 to 14, 14 to 16                       // pierna der
)

/**
 * Contorno oscuro que se dibuja debajo de cada trazo. Sin él, el azul del esqueleto se
 * pierde sobre ropa oscura o con poca luz. Es `onSurface` translúcido, no negro puro
 * (regla del design system).
 */
private val HALO_COLOR = Color(0xFF181C22).copy(alpha = 0.45f)

/** Mismo umbral que `PoseModelSpec.confidenceThreshold`, para no filtrar dos veces distinto. */
private const val MIN_CONFIDENCE = 0.25f

private val BONE_WIDTH = 4.dp
private val HALO_EXTRA_WIDTH = 3.dp
private val JOINT_RADIUS = 5.dp

/** Tolerancia antes de descartar una articulación por caer fuera del visor. */
private val OUT_OF_BOUNDS_MARGIN = 24.dp
