package com.steadypath.app.presentation.screens.dashboard

import com.steadypath.app.domain.model.DailyActivity
import com.steadypath.app.domain.model.Session
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ActividadConSesionesLocalesTest {

    private val fechaLocal: (String) -> LocalDate? = { LocalDate.parse(it.take(10)) }

    private fun sesion(fecha: String, minutos: Double = 10.0, estado: String = "Completada") = Session(
        idSesion = "s-$fecha-$minutos",
        idPaciente = "p",
        idRutina = "r",
        nombreRutina = "Rutina",
        fechaHoraInicio = "${fecha}T10:00:00Z",
        duracionMinutos = minutos,
        estado = estado,
        resultados = emptyList()
    )

    @Test
    fun `sin sesiones locales queda lo del servidor`() {
        val delServidor = listOf(DailyActivity("2026-11-10", 20.0, 1))
        assertEquals(delServidor, actividadConSesionesLocales(delServidor, emptyList(), fechaLocal))
    }

    @Test
    fun `la sesion hecha sin red marca su dia aunque el servidor no la tenga`() {
        val actividad = actividadConSesionesLocales(
            delServidor = emptyList(),
            historial = listOf(sesion("2026-11-12", minutos = 12.0)),
            fechaLocal = fechaLocal
        )
        assertEquals(listOf(DailyActivity("2026-11-12", 12.0, 1)), actividad)
    }

    @Test
    fun `un dia que el servidor ya cuenta no se suma dos veces`() {
        val delServidor = listOf(DailyActivity("2026-11-12", 12.0, 1))
        val actividad = actividadConSesionesLocales(
            delServidor = delServidor,
            historial = listOf(sesion("2026-11-12", minutos = 12.0)),
            fechaLocal = fechaLocal
        )
        assertEquals(delServidor, actividad)
    }

    @Test
    fun `reemplaza el dia que el servidor traia sin actividad`() {
        val actividad = actividadConSesionesLocales(
            delServidor = listOf(
                DailyActivity("2026-11-11", 15.0, 1),
                DailyActivity("2026-11-12", 0.0, 0)
            ),
            historial = listOf(sesion("2026-11-12", 8.0), sesion("2026-11-12", 4.0)),
            fechaLocal = fechaLocal
        )
        assertEquals(
            listOf(DailyActivity("2026-11-11", 15.0, 1), DailyActivity("2026-11-12", 12.0, 2)),
            actividad
        )
    }

    @Test
    fun `una sesion que no se completo no cuenta como actividad`() {
        val actividad = actividadConSesionesLocales(
            delServidor = emptyList(),
            historial = listOf(sesion("2026-11-12", estado = "Abandonada")),
            fechaLocal = fechaLocal
        )
        assertEquals(emptyList<DailyActivity>(), actividad)
    }
}
