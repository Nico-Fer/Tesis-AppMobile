package com.steadypath.app.presentation.components.bodydiagram

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Una región anatómica dibujable del diagrama corporal.
 *
 * @param id identificador de la región en el SVG original (ej. `"quads-left"`).
 * @param pathData el atributo `d` del `<path>`, en las coordenadas del arte.
 */
internal data class BodyRegion(val id: String, val pathData: String) {

    /**
     * El [pathData] parseado. Se resuelve una sola vez por región: son 89 paths y
     * volver a parsearlos en cada recomposición —o en cada frame de la animación de
     * resaltado— no tiene sentido, la geometría es constante.
     */
    val path: Path by lazy(LazyThreadSafetyMode.NONE) {
        PathParser().parsePathString(pathData).toPath()
    }
}

/**
 * Las dos vistas del arte.
 *
 * En el SVG original ambas conviven en un mismo lienzo: el frente en el viewBox
 * `0 0 35 93` y la espalda corrida a `37 0 35 93`. En vez de hardcodear ese offset,
 * cada vista calcula su propio bounding box y el diagrama encaja *eso* en el canvas
 * — así no quedan márgenes muertos y el corrimiento de la espalda se cancela solo.
 */
internal enum class BodyView(val regions: List<BodyRegion>) {
    FRONT(FRONT_REGIONS),
    BACK(BACK_REGIONS);

    /**
     * Caja que contiene a todas las regiones de la vista.
     *
     * Ojo: `Path.getBounds()` en Android mide los puntos de control de las curvas,
     * así que puede sobrar un pelo. Alcanza para encuadrar.
     */
    val bounds: Rect by lazy(LazyThreadSafetyMode.NONE) {
        regions.map { it.path.getBounds() }.reduce { acc, r ->
            Rect(
                left = minOf(acc.left, r.left),
                top = minOf(acc.top, r.top),
                right = maxOf(acc.right, r.right),
                bottom = maxOf(acc.bottom, r.bottom)
            )
        }
    }

    /** Relación ancho/alto de la figura, para el `Modifier.aspectRatio` del canvas. */
    val aspectRatio: Float get() = bounds.width / bounds.height
}
