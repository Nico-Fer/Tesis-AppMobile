package com.steadypath.app.presentation.components

import com.steadypath.app.domain.model.EstadoDeSincronizacion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvisoParaTest {

    private fun aviso(estado: EstadoDeSincronizacion, conDatosGuardados: Boolean = true) =
        avisoPara(estado, conDatosGuardados)

    @Test
    fun `en linea y sin pendientes no hay nada que avisar`() {
        assertNull(aviso(EstadoDeSincronizacion()))
    }

    @Test
    fun `sin conexion avisa que se ven datos guardados`() {
        val aviso = aviso(EstadoDeSincronizacion(enLinea = false))
        assertEquals(TipoDeAviso.SIN_CONEXION, aviso?.tipo)
        assertEquals("Sin conexión · Viendo datos guardados", aviso?.texto)
    }

    @Test
    fun `en el ingreso no se habla de datos guardados`() {
        val aviso = aviso(EstadoDeSincronizacion(enLinea = false), conDatosGuardados = false)
        assertEquals("Sin conexión a internet", aviso?.texto)
    }

    @Test
    fun `sin conexion y con pendientes dice cuantos`() {
        assertEquals(
            "Sin conexión · 1 cambio por sincronizar",
            aviso(EstadoDeSincronizacion(enLinea = false, pendientes = 1))?.texto
        )
        assertEquals(
            "Sin conexión · 3 cambios por sincronizar",
            aviso(EstadoDeSincronizacion(enLinea = false, pendientes = 3))?.texto
        )
    }

    @Test
    fun `con red y pendientes avisa que faltan subir`() {
        val aviso = aviso(EstadoDeSincronizacion(pendientes = 2))
        assertEquals(TipoDeAviso.PENDIENTES, aviso?.tipo)
        assertEquals("2 cambios por sincronizar", aviso?.texto)
    }

    @Test
    fun `la sesion vencida va primero porque pide una accion`() {
        val aviso = aviso(
            EstadoDeSincronizacion(enLinea = false, pendientes = 2, rechazadas = 1, sesionVencida = true)
        )
        assertEquals(TipoDeAviso.SESION_VENCIDA, aviso?.tipo)
        assertEquals("Tu sesión venció. Tocá para iniciar sesión y sincronizar.", aviso?.texto)
    }

    @Test
    fun `sin nada pendiente la sesion vencida no promete sincronizar`() {
        val aviso = aviso(EstadoDeSincronizacion(sesionVencida = true))
        assertEquals("Tu sesión venció. Tocá para iniciar sesión.", aviso?.texto)
    }

    @Test
    fun `en el ingreso no se avisa que la sesion vencio`() {
        assertNull(aviso(EstadoDeSincronizacion(sesionVencida = true), conDatosGuardados = false))
    }

    @Test
    fun `los rechazados se avisan antes que el estado de la red`() {
        val aviso = aviso(EstadoDeSincronizacion(enLinea = false, rechazadas = 1))
        assertEquals(TipoDeAviso.RECHAZADOS, aviso?.tipo)
        assertEquals("No se pudo sincronizar 1 cambio. Tocá para cerrar.", aviso?.texto)
    }
}
