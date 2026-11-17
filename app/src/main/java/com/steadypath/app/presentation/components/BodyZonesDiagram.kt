package com.steadypath.app.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.steadypath.app.presentation.components.bodydiagram.BodyView
import com.steadypath.app.presentation.components.bodydiagram.regionsFor
import com.steadypath.app.presentation.ui.theme.DomiTheme

/**
 * Cuánto se engorda cada región para armar el fondo, en **unidades del arte**.
 *
 * El arte no trae silueta: son islas de músculo separadas por huecos anchos. Si se
 * dibujan tal cual, el fondo de la card se cuela por las costuras y la figura queda
 * facetada. Engordando cada región en el color de línea y pintando los músculos
 * encima al tamaño original, el hueco *pasa a ser* la línea anatómica y el sobrante
 * de afuera queda como contorno del cuerpo.
 *
 * Va en unidades del arte y no en dp a propósito: la línea tiene que escalar con la
 * figura, igual que en el SVG original.
 */
private const val BACKDROP_UNITS = 0.5f

/**
 * Diagrama anatómico del cuerpo humano (vista frontal y posterior) que resalta los
 * músculos que involucra un ejercicio.
 *
 * El cuerpo se dibuja como línea: cada músculo es una región cerrada del arte SVG
 * (ver `bodydiagram/BodyArtwork*.kt`), con relleno tenue y contorno fino. Las regiones
 * activas se rellenan con `primary`.
 *
 * @param highlightedZones etiquetas tal como vienen del dominio: músculos específicos
 * ("Gemelos", "Cuádriceps", "Glúteos"…) o zonas amplias legadas ("Brazos", "Piernas"),
 * que se expanden a sus músculos. Insensible a tildes y mayúsculas.
 */
@Composable
fun BodyZonesDiagram(
    highlightedZones: List<String>,
    modifier: Modifier = Modifier
) {
    val activeRegions = remember(highlightedZones) { regionsFor(highlightedZones) }

    // Fade-in del resaltado al entrar a la pantalla o cambiar de ejercicio.
    val reveal = remember(activeRegions) { Animatable(0f) }
    LaunchedEffect(reveal) { reveal.animateTo(1f, tween(durationMillis = 700)) }

    val fillColor = MaterialTheme.colorScheme.surfaceContainerHighest
    // `outline` y no `outlineVariant`: con la variante el detalle anatómico se pierde,
    // sobre todo en tema oscuro, donde queda casi al mismo valor que el relleno.
    val lineColor = MaterialTheme.colorScheme.outline
    val highlightColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BodyFigure(
            view = BodyView.FRONT,
            label = "Frente",
            activeRegions = activeRegions,
            fillColor = fillColor,
            lineColor = lineColor,
            highlightColor = highlightColor,
            revealFraction = reveal.value,
            modifier = Modifier.fillMaxHeight()
        )
        BodyFigure(
            view = BodyView.BACK,
            label = "Espalda",
            activeRegions = activeRegions,
            fillColor = fillColor,
            lineColor = lineColor,
            highlightColor = highlightColor,
            revealFraction = reveal.value,
            modifier = Modifier.fillMaxHeight()
        )
    }
}

@Composable
private fun BodyFigure(
    view: BodyView,
    label: String,
    activeRegions: Set<String>,
    fillColor: Color,
    lineColor: Color,
    highlightColor: Color,
    revealFraction: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(view.aspectRatio, matchHeightConstraintsFirst = true)
        ) {
            drawBody(view, activeRegions, fillColor, lineColor, highlightColor, revealFraction)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun DrawScope.drawBody(
    view: BodyView,
    activeRegions: Set<String>,
    fillColor: Color,
    lineColor: Color,
    highlightColor: Color,
    revealFraction: Float
) {
    val bounds = view.bounds
    // Encaja el bounding box del arte en el canvas, centrado y sin deformar.
    val scale = minOf(size.width / bounds.width, size.height / bounds.height)
    val dx = (size.width - bounds.width * scale) / 2f
    val dy = (size.height - bounds.height * scale) / 2f

    // `join = Round` evita las púas que deja el miter en los vértices agudos del arte.
    val backdrop = Stroke(width = BACKDROP_UNITS, join = StrokeJoin.Round)

    val highlight = lerp(fillColor, highlightColor, revealFraction)

    withTransform({
        translate(dx, dy)
        scale(scale, scale, pivot = Offset.Zero)
        // Lleva el arte al origen; en la espalda esto cancela el offset x=37 del SVG.
        translate(-bounds.left, -bounds.top)
    }) {
        // 1) Fondo: cada región engordada en el color de línea. Lo que quede visible
        //    después de la pasada 2 son las separaciones entre músculos y el contorno.
        view.regions.forEach { region ->
            drawPath(region.path, lineColor, style = backdrop)
            drawPath(region.path, lineColor)
        }

        // 2) Los músculos, al tamaño original, encima del fondo.
        view.regions.forEach { region ->
            drawPath(region.path, if (region.id in activeRegions) highlight else fillColor)
        }
    }
}

// ── Previews ────────────────────────────────────────────────────────────────
// La forma más rápida de iterar el arte sin levantar el backend.

@Preview(name = "Cuádriceps", widthDp = 320, heightDp = 300, showBackground = true)
@Composable
private fun BodyZonesDiagramQuadsPreview() {
    DomiTheme {
        PreviewFrame { BodyZonesDiagram(highlightedZones = listOf("Cuádriceps")) }
    }
}

@Preview(name = "Dorsales + Tríceps", widthDp = 320, heightDp = 300, showBackground = true)
@Composable
private fun BodyZonesDiagramBackPreview() {
    DomiTheme {
        PreviewFrame { BodyZonesDiagram(highlightedZones = listOf("Dorsales", "Tríceps")) }
    }
}

@Preview(name = "Piernas (zona amplia)", widthDp = 320, heightDp = 300, showBackground = true)
@Composable
private fun BodyZonesDiagramLegsPreview() {
    DomiTheme {
        PreviewFrame { BodyZonesDiagram(highlightedZones = listOf("Piernas")) }
    }
}

@Preview(name = "Sin resaltado", widthDp = 320, heightDp = 300, showBackground = true)
@Composable
private fun BodyZonesDiagramEmptyPreview() {
    DomiTheme {
        PreviewFrame { BodyZonesDiagram(highlightedZones = emptyList()) }
    }
}

@Preview(name = "Hombros — oscuro", widthDp = 320, heightDp = 300, showBackground = true)
@Composable
private fun BodyZonesDiagramDarkPreview() {
    DomiTheme(darkTheme = true) {
        PreviewFrame { BodyZonesDiagram(highlightedZones = listOf("Hombros")) }
    }
}

/**
 * Replica la card que aloja al diagrama en la pantalla, para previsualizarlo en contexto.
 *
 * El alto fijo no es decorativo: las figuras usan `fillMaxHeight` + `weight`, así que
 * necesitan un alto acotado. En la pantalla real lo da el `Box` de [ZonesTab].
 */
@Composable
private fun PreviewFrame(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(16.dp)
            .height(240.dp)
    ) {
        content()
    }
}
