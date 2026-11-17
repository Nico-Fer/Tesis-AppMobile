package com.steadypath.app.data.local

import com.steadypath.app.domain.model.NoEncontradoException
import com.steadypath.app.domain.model.PermissionDeniedException
import com.steadypath.app.domain.model.ServidorNoDisponibleException
import com.steadypath.app.domain.model.SinConexionException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FlujoConCacheTest {

    /** Copia local y servidor de mentira, con registro de lo que se les pidió. */
    private class Escenario(
        var copia: String?,
        val respuesta: Result<String>,
        val hayRed: Boolean = true
    ) {
        var llamadas = 0
        var borrados = 0

        fun correr(): List<Result<String>> = runBlocking {
            flujoConCache(
                hayRed = { hayRed },
                leer = { copia },
                llamar = { llamadas++; respuesta },
                guardar = { copia = it },
                borrar = { borrados++; copia = null },
                aDominio = { it }
            ).toList()
        }
    }

    @Test
    fun `con copia y red emite la copia y despues lo del servidor`() {
        val escenario = Escenario(copia = "viejo", respuesta = Result.success("nuevo"))
        val emitido = escenario.correr()
        assertEquals(listOf(Result.success("viejo"), Result.success("nuevo")), emitido)
        assertEquals("nuevo", escenario.copia)
    }

    @Test
    fun `con copia y sin red emite la copia y no espera una llamada perdida`() {
        val escenario = Escenario(
            copia = "viejo",
            respuesta = Result.failure(SinConexionException("sin red")),
            hayRed = false
        )
        assertEquals(listOf(Result.success("viejo")), escenario.correr())
        assertEquals(0, escenario.llamadas)
    }

    @Test
    fun `sin copia intenta la llamada aunque el monitor diga que no hay red`() {
        val escenario = Escenario(copia = null, respuesta = Result.success("nuevo"), hayRed = false)
        assertEquals(listOf(Result.success("nuevo")), escenario.correr())
        assertEquals(1, escenario.llamadas)
    }

    @Test
    fun `si la llamada falla por un corte la pantalla se queda con la copia`() {
        val escenario = Escenario(
            copia = "viejo",
            respuesta = Result.failure(ServidorNoDisponibleException("503"))
        )
        assertEquals(listOf(Result.success("viejo")), escenario.correr())
        assertEquals("viejo", escenario.copia)
        assertEquals(0, escenario.borrados)
    }

    @Test
    fun `sin copia y sin respuesta emite el error`() {
        val error = SinConexionException("sin red")
        val escenario = Escenario(copia = null, respuesta = Result.failure(error))
        val emitido = escenario.correr()
        assertEquals(1, emitido.size)
        assertSame(error, emitido.single().exceptionOrNull())
    }

    @Test
    fun `un 403 borra la copia y emite el error para que deje de mostrarse`() {
        val error = PermissionDeniedException()
        val escenario = Escenario(copia = "datos del paciente", respuesta = Result.failure(error))
        val emitido = escenario.correr()
        assertEquals(Result.success("datos del paciente"), emitido.first())
        assertSame(error, emitido.last().exceptionOrNull())
        assertEquals(null, escenario.copia)
        assertEquals(1, escenario.borrados)
    }

    @Test
    fun `un 404 tambien invalida la copia`() {
        val escenario = Escenario(copia = "rutina", respuesta = Result.failure(NoEncontradoException("no existe")))
        escenario.correr()
        assertEquals(null, escenario.copia)
    }

    @Test
    fun `siempre emite al menos una vez`() {
        val casos = listOf(
            Escenario("c", Result.success("n")),
            Escenario("c", Result.failure(SinConexionException("x"))),
            Escenario(null, Result.success("n")),
            Escenario(null, Result.failure(SinConexionException("x"))),
            Escenario(null, Result.failure(PermissionDeniedException())),
            Escenario("c", Result.success("n"), hayRed = false),
            Escenario(null, Result.failure(SinConexionException("x")), hayRed = false)
        )
        casos.forEach { assertTrue(it.correr().isNotEmpty()) }
    }

    @Test
    fun `solo el rechazo del servidor invalida la copia`() {
        assertTrue(invalidaLaCopia(PermissionDeniedException()))
        assertTrue(invalidaLaCopia(NoEncontradoException("no existe")))
        assertFalse(invalidaLaCopia(SinConexionException("sin red")))
        assertFalse(invalidaLaCopia(ServidorNoDisponibleException("503")))
        assertFalse(invalidaLaCopia(Exception("otro")))
    }

    @Test
    fun `una base local que falla no impide usar la red`() {
        val emitido = runBlocking {
            flujoConCache<String, String>(
                hayRed = { true },
                leer = { error("base rota") },
                llamar = { Result.success("nuevo") },
                guardar = { error("base rota") },
                borrar = {},
                aDominio = { it }
            ).toList()
        }
        assertEquals(listOf(Result.success("nuevo")), emitido)
    }
}
