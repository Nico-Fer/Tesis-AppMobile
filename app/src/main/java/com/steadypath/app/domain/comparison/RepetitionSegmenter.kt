package com.steadypath.app.domain.comparison

/**
 * Segmenta el stream de [AngleFrame] en repeticiones del ejercicio.
 *
 * v1 es batch por repetición: acumula cuadros y devuelve la repetición completa cuando
 * detecta su cierre. La comparación DTW corre entonces repetición-contra-repetición.
 *
 * TODO: DTW incremental (feedback mid-repetición) — el DTW clásico requiere ambas
 * secuencias completas; dar feedback durante el movimiento exige una variante streaming
 * (p. ej. SPRING/OEM-DTW) que entraría como otra implementación de este seam, emitiendo
 * resultados parciales sin tocar la extracción de ángulos ni el núcleo DTW. Decisión
 * metodológica explícita: v1 corrige al cierre de cada repetición, no durante.
 */
interface RepetitionSegmenter {
    /**
     * Procesa un cuadro. Devuelve los cuadros de una repetición completa cuando este
     * cuadro la cierra, o `null` mientras la repetición sigue en curso.
     */
    fun onFrame(frame: AngleFrame): List<AngleFrame>?

    /** Descarta todo el estado acumulado (cambio de ejercicio / reinicio de sesión). */
    fun reset()
}

/**
 * Fábrica para crear un segmentador por sesión: el segmentador es stateful y el índice
 * del ángulo conductor sale de la referencia cargada en runtime.
 *
 * [driverAmplitudeRad] es la amplitud des-envuelta del conductor en la referencia
 * ([ReferenceAlignment.profileOf]) para derivar umbrales por ejercicio; `null` cuando no
 * hay referencia (flujo de captura) y rigen los defaults.
 */
fun interface RepetitionSegmenterFactory {
    fun create(driverAngleIndex: Int, driverAmplitudeRad: Float?): RepetitionSegmenter
}
