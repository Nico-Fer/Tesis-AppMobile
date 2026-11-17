package com.steadypath.app.data.datasource

import com.steadypath.app.data.sync.EnvioDePendientes
import com.steadypath.app.domain.model.ConflictoException
import com.steadypath.app.domain.model.NotProvisionedException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un 409 es "usuario sin provisionar" solo cuando el backend lo dice; el resto, conflicto. */
class Conflicto409Test {

    @Test
    fun `el 409 de usuario sin provisionar sigue siendo NotProvisioned`() {
        val error = excepcionPara409(
            "El usuario esta autenticado pero no fue provisionado. Llamar a POST /api/usuarios/provision."
        )
        assertTrue(error is NotProvisionedException)
    }

    @Test
    fun `una invitacion duplicada es un conflicto con el mensaje del servidor`() {
        val error = excepcionPara409("Ya hay una invitacion pendiente para ese email.")
        assertTrue(error is ConflictoException)
        assertEquals("Ya hay una invitacion pendiente para ese email.", error.message)
    }

    @Test
    fun `la cola de sesiones no reintenta un conflicto`() {
        assertFalse(EnvioDePendientes.esReintentable(ConflictoException("La sesion pertenece a otro paciente.")))
        assertTrue(EnvioDePendientes.esReintentable(NotProvisionedException()))
    }
}
