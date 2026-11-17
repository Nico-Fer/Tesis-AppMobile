package com.steadypath.app.data.vision

/**
 * Parámetros del letterbox aplicado al redimensionar la imagen original a la entrada
 * cuadrada del modelo (640×640) manteniendo el aspect ratio.
 *
 * La transformación directa de un punto original `(xo, yo)` al espacio del modelo es:
 * ```
 * xm = xo * scale + padX
 * ym = yo * scale + padY
 * ```
 * Para revertirla (des-letterboxing de un keypoint en espacio de modelo a original):
 * ```
 * xo = (xm - padX) / scale
 * yo = (ym - padY) / scale
 * ```
 *
 * @property scale factor de escala aplicado a la imagen original (min de los dos ejes).
 * @property padX padding horizontal añadido (en píxeles del espacio del modelo).
 * @property padY padding vertical añadido (en píxeles del espacio del modelo).
 */
data class LetterboxParams(
    val scale: Float,
    val padX: Float,
    val padY: Float
)
