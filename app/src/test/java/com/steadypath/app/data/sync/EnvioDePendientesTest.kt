package com.steadypath.app.data.sync

import com.google.gson.Gson
import com.steadypath.app.data.dto.CreateSessionRequestDto
import com.steadypath.app.domain.model.NoAutenticadoException
import com.steadypath.app.domain.model.NoEncontradoException
import com.steadypath.app.domain.model.NotProvisionedException
import com.steadypath.app.domain.model.PermissionDeniedException
import com.steadypath.app.domain.model.ServidorNoDisponibleException
import com.steadypath.app.domain.model.SinConexionException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvioDePendientesTest {

    private fun pendiente(id: String, tipo: TipoDeOperacion = TipoDeOperacion.SESION) =
        OperacionPendiente(
            id = id,
            idUsuario = "paciente-1",
            tipo = tipo,
            destino = id,
            cargaJson = "{}"
        )

    private data class Corrida(
        val resumen: EnvioDePendientes.Resumen,
        val intentadas: List<String>,
        val quitadas: List<String>,
        val deshechas: List<String>
    )

    /** Ejecuta [EnvioDePendientes.procesar] con un resultado fijo por id. */
    private fun procesar(
        pendientes: List<OperacionPendiente>,
        resultados: Map<String, Result<Unit>>
    ): Corrida = runBlocking {
        val intentadas = mutableListOf<String>()
        val quitadas = mutableListOf<String>()
        val deshechas = mutableListOf<String>()
        val resumen = EnvioDePendientes.procesar(
            pendientes,
            enviar = { intentadas += it.id; resultados.getValue(it.id) },
            quitar = { quitadas += it },
            alDescartar = { operacion, _ -> deshechas += operacion.id }
        )
        Corrida(resumen, intentadas, quitadas, deshechas)
    }

    @Test
    fun `los errores transitorios son reintentables`() {
        assertTrue(EnvioDePendientes.esReintentable(SinConexionException("sin red")))
        assertTrue(EnvioDePendientes.esReintentable(ServidorNoDisponibleException("503")))
        assertTrue(EnvioDePendientes.esReintentable(NoAutenticadoException("401")))
        assertTrue(EnvioDePendientes.esReintentable(NotProvisionedException()))
    }

    @Test
    fun `un rechazo definitivo del servidor no es reintentable`() {
        assertFalse(EnvioDePendientes.esReintentable(PermissionDeniedException()))
        assertFalse(EnvioDePendientes.esReintentable(NoEncontradoException("no existe")))
        assertFalse(EnvioDePendientes.esReintentable(Exception("Datos inválidos")))
    }

    @Test
    fun `las enviadas salen de la cola`() {
        val corrida = procesar(
            listOf(pendiente("a"), pendiente("b")),
            mapOf("a" to Result.success(Unit), "b" to Result.success(Unit))
        )
        assertEquals(EnvioDePendientes.Resumen(enviadas = 2, descartadas = 0), corrida.resumen)
        assertEquals(listOf("a", "b"), corrida.quitadas)
        assertTrue(corrida.deshechas.isEmpty())
    }

    @Test
    fun `sin red corta y deja esa y las siguientes en la cola`() {
        val corrida = procesar(
            listOf(pendiente("a"), pendiente("b"), pendiente("c")),
            mapOf(
                "a" to Result.success(Unit),
                "b" to Result.failure(SinConexionException("sin red")),
                "c" to Result.success(Unit)
            )
        )
        assertEquals(
            EnvioDePendientes.Resumen(enviadas = 1, descartadas = 0, quedanPendientes = true),
            corrida.resumen
        )
        assertEquals(listOf("a"), corrida.quitadas)
        // La que sigue a la que falló ni se intenta: saltearla rompería el orden.
        assertEquals(listOf("a", "b"), corrida.intentadas)
    }

    @Test
    fun `un rechazo definitivo se descarta y se deshace sin bloquear a las demas`() {
        val corrida = procesar(
            listOf(pendiente("a"), pendiente("b")),
            mapOf(
                "a" to Result.failure(PermissionDeniedException()),
                "b" to Result.success(Unit)
            )
        )
        assertEquals(EnvioDePendientes.Resumen(enviadas = 1, descartadas = 1), corrida.resumen)
        assertEquals(listOf("a", "b"), corrida.quitadas)
        assertEquals(listOf("a"), corrida.deshechas)
    }

    @Test
    fun `la rutina creada sin red se envia antes que la sesion hecha con ella`() {
        val corrida = procesar(
            listOf(
                pendiente("rutina", TipoDeOperacion.RUTINA_PROPIA),
                pendiente("sesion", TipoDeOperacion.SESION),
                pendiente("perfil", TipoDeOperacion.PERFIL_PACIENTE)
            ),
            mapOf(
                "rutina" to Result.success(Unit),
                "sesion" to Result.success(Unit),
                "perfil" to Result.success(Unit)
            )
        )
        assertEquals(listOf("rutina", "sesion", "perfil"), corrida.intentadas)
    }

    @Test
    fun `si la rutina no pudo subir su sesion espera en la cola`() {
        val corrida = procesar(
            listOf(pendiente("rutina", TipoDeOperacion.RUTINA_PROPIA), pendiente("sesion")),
            mapOf(
                "rutina" to Result.failure(ServidorNoDisponibleException("503")),
                "sesion" to Result.success(Unit)
            )
        )
        assertEquals(listOf("rutina"), corrida.intentadas)
        assertTrue(corrida.quitadas.isEmpty())
        assertTrue(corrida.resumen.quedanPendientes)
    }

    @Test
    fun `un 401 corta el envio y avisa que la sesion vencio`() {
        val corrida = procesar(
            listOf(pendiente("a")),
            mapOf("a" to Result.failure(NoAutenticadoException("401")))
        )
        assertTrue(corrida.resumen.quedanPendientes)
        assertTrue(corrida.resumen.sesionVencida)
        assertTrue(corrida.quitadas.isEmpty())
    }

    @Test
    fun `un corte de red no se confunde con sesion vencida`() {
        val corrida = procesar(
            listOf(pendiente("a")),
            mapOf("a" to Result.failure(SinConexionException("sin red")))
        )
        assertTrue(corrida.resumen.quedanPendientes)
        assertFalse(corrida.resumen.sesionVencida)
    }

    @Test
    fun `solo se fusionan las operaciones que reemplazan`() {
        assertFalse(TipoDeOperacion.SESION.seFusiona)
        assertFalse(TipoDeOperacion.RUTINA_PROPIA.seFusiona)
        assertTrue(TipoDeOperacion.REFERENCIA_PROPIA.seFusiona)
        assertTrue(TipoDeOperacion.PERFIL_PACIENTE.seFusiona)
    }

    // --- Reasignación de rutina: backend que le pone su propio id a la rutina ---

    private val gson = Gson()

    private fun sesionDe(idRutina: String?) = gson.toJson(
        CreateSessionRequestDto(
            idCliente = "sesion-1",
            idRutina = idRutina,
            fechaHoraInicio = "2026-10-15T10:00:00Z",
            duracionMinutos = 5.0,
            estado = "Completada",
            resultados = emptyList()
        )
    )

    @Test
    fun `la sesion de la rutina local pasa a citar la del servidor sin perder sus datos`() {
        val nueva = reasignarRutinaDeSesion(sesionDe("local"), "local", "servidor", gson)
        val solicitud = gson.fromJson(nueva, CreateSessionRequestDto::class.java)
        assertEquals("servidor", solicitud.idRutina)
        assertEquals("sesion-1", solicitud.idCliente)
        assertEquals("2026-10-15T10:00:00Z", solicitud.fechaHoraInicio)
    }

    @Test
    fun `una sesion de otra rutina o de practica libre no se toca`() {
        assertNull(reasignarRutinaDeSesion(sesionDe("otra"), "local", "servidor", gson))
        assertNull(reasignarRutinaDeSesion(sesionDe(null), "local", "servidor", gson))
    }

    @Test
    fun `una carga ilegible no se reasigna`() {
        assertNull(reasignarRutinaDeSesion("no es json", "local", "servidor", gson))
    }
}
