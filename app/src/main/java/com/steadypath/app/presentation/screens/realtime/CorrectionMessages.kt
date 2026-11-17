package com.steadypath.app.presentation.screens.realtime

import com.steadypath.app.domain.comparison.CorrectionHint
import com.steadypath.app.domain.comparison.ErrorDirection
import com.steadypath.app.domain.comparison.RepPhase
import java.text.Normalizer

/**
 * Traduce un [CorrectionHint] a un mensaje es-AR, corto y accionable, pensado para
 * adultos mayores que escuchan la corrección (TTS) sin mirar la pantalla.
 *
 * El mensaje dice **qué parte del cuerpo** mover y **en qué sentido**, con el verbo propio
 * de cada ejercicio: en la prueba con usuarios, dos de cuatro participantes y el profesional
 * señalaron que la corrección no decía qué cambiar. La familia del ejercicio se deduce del
 * nombre (la referencia no guarda el patrón); si no se reconoce, el mensaje cae al genérico
 * por articulación.
 *
 * La corrección es la misma sea quien sea que registró la referencia. Con la propia
 * terminaba en «como en tu referencia», y la prueba con usuarios mostró que confundía más de
 * lo que aclaraba. Ninguna corrección afirma que algo esté bien o mal: solo dice qué
 * cambiar. La diferencia por respaldo (RNF-19) queda en el mensaje de repetición lograda.
 * Ningún mensaje lleva porcentaje: hablado, no le dice nada a la persona.
 */
object CorrectionMessages {

    internal enum class Familia { BRAZOS, SENTADILLA, SILLA, EXTENSION_RODILLA, FLEXION_RODILLA, TALONES }

    /** @param exerciseName nombre del ejercicio tal como lo muestra la app. */
    fun messageFor(hint: CorrectionHint, exerciseName: String? = null): String =
        baseMessage(hint, familiaDe(exerciseName))

    /** Sin atribución de la articulación: lo único que se puede decir es la dirección general. */
    fun sinAtribucion(): String = "Ajustá el movimiento"

    internal fun familiaDe(exerciseName: String?): Familia? {
        val n = exerciseName?.let { sinTildes(it.lowercase()) } ?: return null
        return when {
            "sentadilla" in n -> Familia.SENTADILLA
            "talon" in n -> Familia.TALONES
            "silla" in n || "sentarse" in n -> Familia.SILLA
            "extension" in n && ("rodilla" in n || "pierna" in n) -> Familia.EXTENSION_RODILLA
            "flexion" in n && "rodilla" in n -> Familia.FLEXION_RODILLA
            "hombro" in n || "brazo" in n -> Familia.BRAZOS
            else -> null
        }
    }

    private fun baseMessage(hint: CorrectionHint, familia: Familia?): String {
        val fase = phaseText(hint.phase)
        when (hint.direction) {
            ErrorDirection.TOO_SLOW -> return "Un poco más rápido $fase"
            ErrorDirection.TOO_FAST -> return "Más lento $fase, con control"
            else -> Unit
        }
        val falta = hint.direction == ErrorDirection.UNDERSHOOT
        val joint = hint.angleName.substringBeforeLast('_')

        val especifico: String? = when (familia) {
            Familia.BRAZOS -> when (joint) {
                "hombro" -> if (falta) "Levantá más los brazos" else "No subas tanto los brazos"
                "codo" -> "Mantené los brazos estirados"
                else -> null
            }
            Familia.SENTADILLA -> when (joint) {
                "cadera" -> if (falta) "Bajá un poco más, doblando las rodillas" else "No bajes tanto"
                "rodilla" -> if (falta) "Doblá un poco más las rodillas" else "No dobles tanto las rodillas"
                else -> null
            }
            Familia.SILLA -> when {
                joint == "cadera" && !hint.isDriver -> "Mantené el tronco firme"
                joint == "rodilla" || joint == "cadera" ->
                    if (falta) "Hacé el movimiento completo: parate del todo" else "Bajá a la silla con control"
                else -> null
            }
            Familia.EXTENSION_RODILLA -> when (joint) {
                "rodilla" -> if (falta) "Estirá más la pierna" else "Estirá la pierna sin forzar la rodilla"
                "cadera" -> "Mantené la espalda apoyada"
                else -> null
            }
            Familia.FLEXION_RODILLA -> when (joint) {
                "rodilla" -> if (falta) "Doblá más la rodilla" else "No dobles tanto la rodilla"
                "cadera" -> "Mantené el tronco firme"
                else -> null
            }
            Familia.TALONES -> when (joint) {
                "rodilla" -> if (falta) "Elevá más los talones" else "No subas tanto los talones"
                "cadera" -> "Quedate bien sentado, sin mover el tronco"
                else -> null
            }
            null -> null
        }
        if (especifico != null) return "$especifico $fase"
        return generico(joint, falta, hint.isDriver, fase)
    }

    /** Mensaje por articulación, para ejercicios que no se reconocen por el nombre. */
    private fun generico(joint: String, falta: Boolean, isDriver: Boolean, fase: String): String {
        // La cadera solo es control postural cuando NO conduce el ejercicio (sentadilla:
        // la cadera es el movimiento en sí, no una compensación del tronco).
        if (joint == "cadera" && !isDriver) return "Mantené el tronco firme, sin compensar"
        return if (falta) when (joint) {
            "hombro" -> "Levantá más el brazo $fase"
            "codo" -> "Estirá más el codo $fase"
            "rodilla" -> "Ampliá el movimiento de la rodilla $fase"
            "cadera" -> "Bajá un poco más $fase"
            else -> "Hacé el movimiento más amplio $fase"
        } else when (joint) {
            "hombro" -> "No subas tanto el brazo $fase"
            "codo" -> "No fuerces el codo $fase"
            "rodilla" -> "No exijas tanto la rodilla $fase"
            "cadera" -> "No bajes tanto $fase"
            else -> "Hacé el movimiento más corto $fase"
        }
    }

    private fun phaseText(phase: RepPhase): String = when (phase) {
        RepPhase.SUBIDA -> "al subir"
        RepPhase.PICO -> "en el punto más alto"
        RepPhase.BAJADA -> "al bajar"
    }

    private fun sinTildes(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
}
