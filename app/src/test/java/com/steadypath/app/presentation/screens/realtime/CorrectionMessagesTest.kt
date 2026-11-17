package com.steadypath.app.presentation.screens.realtime

import com.steadypath.app.domain.comparison.CorrectionHint
import com.steadypath.app.domain.comparison.ErrorDirection
import com.steadypath.app.domain.comparison.RepPhase
import com.steadypath.app.presentation.screens.realtime.CorrectionMessages.Familia
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionMessagesTest {

    private fun hint(
        angulo: String,
        direccion: ErrorDirection = ErrorDirection.UNDERSHOOT,
        fase: RepPhase = RepPhase.SUBIDA,
        conductor: Boolean = true
    ) = CorrectionHint(
        angleIndex = 0, angleName = angulo, direction = direccion, phase = fase,
        severity = 1.5f, isDriver = conductor
    )

    @Test
    fun `reconoce la familia por el nombre, con o sin tildes`() {
        assertEquals(Familia.BRAZOS, CorrectionMessages.familiaDe("Elevación de hombros"))
        assertEquals(Familia.SENTADILLA, CorrectionMessages.familiaDe("Sentadilla parcial"))
        assertEquals(Familia.TALONES, CorrectionMessages.familiaDe("Elevación de talones"))
        assertEquals(Familia.EXTENSION_RODILLA, CorrectionMessages.familiaDe("Extension de rodillas"))
        assertEquals(Familia.SILLA, CorrectionMessages.familiaDe("Sentarse y levantarse de una silla"))
        assertNull(CorrectionMessages.familiaDe("Ejercicio nuevo"))
        assertNull(CorrectionMessages.familiaDe(null))
    }

    @Test
    fun `nombra la parte del cuerpo y el sentido segun el ejercicio`() {
        assertEquals(
            "Levantá más los brazos al subir",
            CorrectionMessages.messageFor(hint("hombro_izq"), "Elevación de hombros")
        )
        assertEquals(
            "Bajá un poco más, doblando las rodillas en el punto más alto",
            CorrectionMessages.messageFor(hint("cadera_izq", fase = RepPhase.PICO), "Sentadilla parcial")
        )
        assertEquals(
            "Estirá más la pierna al subir",
            CorrectionMessages.messageFor(hint("rodilla_der"), "Extensión de rodillas")
        )
        assertEquals(
            "No subas tanto los talones al bajar",
            CorrectionMessages.messageFor(
                hint("rodilla_izq", ErrorDirection.OVERSHOOT, RepPhase.BAJADA), "Elevación de talones"
            )
        )
    }

    @Test
    fun `ninguna correccion menciona la referencia`() {
        for (nombre in NOMBRES) for (angulo in ANGULOS) for (dir in ErrorDirection.entries) {
            val m = CorrectionMessages.messageFor(hint(angulo, dir), nombre)
            assertFalse("«$m» menciona la referencia", m.lowercase().contains("referencia"))
        }
        assertEquals("Ajustá el movimiento", CorrectionMessages.sinAtribucion())
    }

    @Test
    fun `un ejercicio sin familia conocida usa el mensaje por articulacion`() {
        assertEquals(
            "Ampliá el movimiento de la rodilla al subir",
            CorrectionMessages.messageFor(hint("rodilla_izq"), "Ejercicio nuevo")
        )
        assertEquals(
            "Mantené el tronco firme, sin compensar",
            CorrectionMessages.messageFor(hint("cadera_izq", conductor = false), null)
        )
    }

    @Test
    fun `ningun mensaje lleva porcentaje ni palabras vedadas por el RNF-17`() {
        val vedadas = listOf("%", "diagnóstico", "evaluación", "correcto", "incorrecto", "lesión")
        for (nombre in NOMBRES) for (angulo in ANGULOS) for (dir in ErrorDirection.entries) {
            val m = CorrectionMessages.messageFor(hint(angulo, dir), nombre)
            vedadas.forEach { assertFalse("«$m» contiene «$it»", m.lowercase().contains(it)) }
            assertTrue("«$m» quedó vacío", m.isNotBlank())
        }
    }

    private companion object {
        val NOMBRES = listOf(
            "Elevación de hombros", "Sentadilla parcial", "Elevación de talones",
            "Extensión de rodillas", "Flexión de rodilla", "Sentarse y levantarse de una silla", null
        )
        val ANGULOS = listOf("hombro_izq", "codo_izq", "cadera_izq", "rodilla_izq", "tobillo_izq")
    }
}
