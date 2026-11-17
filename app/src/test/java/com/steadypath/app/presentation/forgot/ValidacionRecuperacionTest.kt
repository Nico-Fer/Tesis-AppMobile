package com.steadypath.app.presentation.forgot

import com.steadypath.app.presentation.screens.forgot.ValidacionRecuperacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidacionRecuperacionTest {

    @Test
    fun `acepta un email con arroba y dominio, aunque tenga espacios alrededor`() {
        assertTrue(ValidacionRecuperacion.emailValido("maria.rodriguez@gmail.com"))
        assertTrue(ValidacionRecuperacion.emailValido("  juan@uade.edu.ar "))
    }

    @Test
    fun `rechaza un email sin arroba, sin dominio o vacio`() {
        assertFalse(ValidacionRecuperacion.emailValido(""))
        assertFalse(ValidacionRecuperacion.emailValido("maria.gmail.com"))
        assertFalse(ValidacionRecuperacion.emailValido("@gmail.com"))
        assertFalse(ValidacionRecuperacion.emailValido("maria@gmail"))
        assertFalse(ValidacionRecuperacion.emailValido("maria@.com"))
        assertFalse(ValidacionRecuperacion.emailValido("maria@gmail."))
    }

    @Test
    fun `pide el codigo antes que nada`() {
        assertEquals(
            "Ingresá el código que te llegó por email",
            ValidacionRecuperacion.problema(" ", "Clave123!", "Clave123!")
        )
    }

    @Test
    fun `rechaza una contrasena mas corta que el minimo`() {
        assertEquals(
            "La contraseña nueva debe tener al menos 8 caracteres",
            ValidacionRecuperacion.problema("123456", "Cl4ve!", "Cl4ve!")
        )
    }

    @Test
    fun `rechaza contrasenas que no coinciden`() {
        assertEquals(
            "Las contraseñas nuevas no coinciden",
            ValidacionRecuperacion.problema("123456", "Clave123!", "Clave124!")
        )
    }

    @Test
    fun `deja enviar cuando el codigo y las contrasenas estan bien`() {
        assertNull(ValidacionRecuperacion.problema("123456", "Clave123!", "Clave123!"))
    }
}
