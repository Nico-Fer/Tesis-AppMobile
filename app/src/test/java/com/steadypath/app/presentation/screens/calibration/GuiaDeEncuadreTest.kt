package com.steadypath.app.presentation.screens.calibration

import com.steadypath.app.presentation.screens.calibration.GuiaDeEncuadre.Problema
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuiaDeEncuadreTest {

    private val guia = GuiaDeEncuadre(esperaMs = 3_000, repeticionMs = 10_000, silencioMinimoMs = 4_000)

    private fun aviso(cuerpo: Boolean = true, luz: Boolean = true, estable: Boolean = true, t: Long) =
        guia.aviso(cuerpo, luz, estable, t)

    @Test
    fun `todo en orden no dice nada`() {
        assertNull(aviso(t = 0))
        assertNull(aviso(t = 10_000))
    }

    @Test
    fun `habla recien cuando el problema se sostiene`() {
        assertNull(aviso(luz = false, t = 0))
        assertNull(aviso(luz = false, t = 2_999))
        assertEquals(Problema.LUZ.aviso, aviso(luz = false, t = 3_000))
    }

    @Test
    fun `un parpadeo reinicia la espera`() {
        assertNull(aviso(cuerpo = false, t = 0))
        assertNull(aviso(cuerpo = true, t = 2_000))
        assertNull(aviso(cuerpo = false, t = 2_500))
        assertNull(aviso(cuerpo = false, t = 5_000))
        assertEquals(Problema.CUERPO.aviso, aviso(cuerpo = false, t = 5_500))
    }

    @Test
    fun `nombra un solo problema por vez, empezando por el cuerpo`() {
        aviso(cuerpo = false, luz = false, estable = false, t = 0)
        assertEquals(Problema.CUERPO.aviso, aviso(cuerpo = false, luz = false, estable = false, t = 3_000))
    }

    @Test
    fun `el mismo aviso no se repite antes de tiempo`() {
        aviso(estable = false, t = 0)
        assertEquals(Problema.MOVIMIENTO.aviso, aviso(estable = false, t = 3_000))
        assertNull(aviso(estable = false, t = 12_999))
        assertEquals(Problema.MOVIMIENTO.aviso, aviso(estable = false, t = 13_000))
    }

    @Test
    fun `al arreglar uno pasa al siguiente respetando el silencio minimo`() {
        aviso(cuerpo = false, luz = false, t = 0)
        assertEquals(Problema.CUERPO.aviso, aviso(cuerpo = false, luz = false, t = 3_000))
        // Entra en cuadro: la luz sigue mal y empieza su propia espera.
        assertNull(aviso(luz = false, t = 3_500))
        assertEquals(Problema.LUZ.aviso, aviso(luz = false, t = 7_000))
    }

    @Test
    fun `pausar reinicia la espera`() {
        aviso(luz = false, t = 0)
        guia.pausar()
        assertNull(aviso(luz = false, t = 3_000))
        assertEquals(Problema.LUZ.aviso, aviso(luz = false, t = 6_000))
    }
}
