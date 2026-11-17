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
 * ([ReferenceAlignment.profileOf]) para derivar umbrales por ejercicio. Es el segmentador
 * del flujo EN VIVO; la grabación de referencias usa [RecordingSegmenterFactory].
 */
fun interface RepetitionSegmenterFactory {
    fun create(driverAngleIndex: Int, driverAmplitudeRad: Float?): RepetitionSegmenter
}

/**
 * Por qué una excursión detectada al grabar no se contó como repetición. No hay motivo por
 * amplitud: la referencia registra lo que la persona puede hacer, por chico que sea.
 */
enum class RepRejectReason {
    /** Duración menor al mínimo plausible para un movimiento voluntario. */
    TOO_SHORT,
    /** Forma distinta de "sale, llega al extremo y vuelve" (NCC baja). */
    BAD_SHAPE,
    /** Excursión en dirección opuesta a la primera repetición aceptada. */
    WRONG_DIRECTION
}

/** Eventos del segmentador de grabación que la UI quiere reflejar. */
interface RecordingSegmenterListener {
    /** Terminó la calibración de reposo: a partir de acá se cuentan repeticiones. */
    fun onCalibrated()
    fun onRejected(reason: RepRejectReason)

    /**
     * Las últimas [count] repeticiones devueltas eran acomodos: llegó una mucho más grande
     * y la cuenta vuelve a empezar desde ella. Se invoca antes de devolver esa repetición.
     */
    fun onRetracted(count: Int)

    /** Traza legible de las decisiones del segmentador, para logcat en debug. */
    fun onDiagnostic(message: String) {}
}

/**
 * Fábrica del segmentador de GRABACIÓN de referencias: no hay referencia todavía, así que
 * los umbrales salen del reposo y el ruido del propio paciente, no de una amplitud previa.
 */
fun interface RecordingSegmenterFactory {
    fun create(driverAngleIndex: Int, listener: RecordingSegmenterListener): RepetitionSegmenter
}
