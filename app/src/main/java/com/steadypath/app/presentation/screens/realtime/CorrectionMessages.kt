package com.steadypath.app.presentation.screens.realtime

import com.steadypath.app.domain.comparison.CorrectionHint
import com.steadypath.app.domain.comparison.ErrorDirection
import com.steadypath.app.domain.comparison.RepPhase

/**
 * Traduce un [CorrectionHint] a un mensaje es-AR, corto y accionable, pensado para
 * adultos mayores que escuchan la corrección (TTS) sin mirar la pantalla.
 *
 * Los nombres de ángulo vienen del catálogo (`rodilla_izq`, `cadera_der`, …): se mapea
 * la articulación a la parte del cuerpo que el paciente entiende. La cadera es un ángulo
 * de control postural: cualquier desvío ahí se corrige igual («tronco firme»).
 */
object CorrectionMessages {

    fun messageFor(hint: CorrectionHint): String {
        val joint = hint.angleName.substringBeforeLast('_')
        val phase = phaseText(hint.phase)
        // La cadera solo es control postural cuando NO conduce el ejercicio (sentadilla:
        // la cadera es el movimiento en sí, no una compensación del tronco).
        if (joint == "cadera" && !hint.isDriver) return "Mantené el tronco firme, sin compensar"
        return when (hint.direction) {
            ErrorDirection.UNDERSHOOT -> when (joint) {
                "hombro" -> "Levantá más el brazo $phase"
                "codo" -> "Estirá más el codo $phase"
                "rodilla" -> "Ampliá el movimiento de la rodilla $phase"
                "cadera" -> "Bajá un poco más $phase"
                else -> "Hacé el movimiento más amplio $phase"
            }
            ErrorDirection.OVERSHOOT -> when (joint) {
                "hombro" -> "No subas tanto el brazo $phase"
                "codo" -> "No fuerces el codo $phase"
                "rodilla" -> "No exijas tanto la rodilla $phase"
                "cadera" -> "No bajes tanto $phase"
                else -> "Hacé el movimiento más corto $phase"
            }
            ErrorDirection.TOO_SLOW -> "Un poco más rápido $phase"
            ErrorDirection.TOO_FAST -> "Más lento $phase, con control"
        }
    }

    private fun phaseText(phase: RepPhase): String = when (phase) {
        RepPhase.SUBIDA -> "al subir"
        RepPhase.PICO -> "en el punto más alto"
        RepPhase.BAJADA -> "al bajar"
    }
}
