package com.steadypath.app.domain.comparison

/**
 * Corrección accionable atribuida a una repetición: qué ángulo se desvió, en qué
 * dirección y en qué fase del ciclo. La capa de presentación la traduce a un mensaje.
 */
data class CorrectionHint(
    val angleIndex: Int,
    val angleName: String,
    val direction: ErrorDirection,
    val phase: RepPhase,
    /** Magnitud relativa del error (1 = justo en el umbral); ordena hints entre sí. */
    val severity: Float,
    /** `true` si el ángulo es el conductor del ejercicio (no un control postural). */
    val isDriver: Boolean = false
)

enum class ErrorDirection {
    /** No alcanzó la amplitud de la referencia. */
    UNDERSHOOT,
    /** Se pasó de la amplitud de la referencia. */
    OVERSHOOT,
    /** Ejecutó la fase más rápido que la referencia. */
    TOO_FAST,
    /** Ejecutó la fase más lento que la referencia. */
    TOO_SLOW
}

/** Fase del ciclo valle→pico→valle del ángulo conductor. */
enum class RepPhase { SUBIDA, PICO, BAJADA }
