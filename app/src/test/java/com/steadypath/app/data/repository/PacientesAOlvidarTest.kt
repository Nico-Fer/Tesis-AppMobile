package com.steadypath.app.data.repository

import com.steadypath.app.data.dto.PatientSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PacientesAOlvidarTest {

    private fun paciente(id: String, revocado: Boolean = false) = PatientSummaryDto(
        idPaciente = id,
        nombre = "Paciente $id",
        email = "$id@ejemplo.com",
        edad = 60,
        cantidadRutinasActivas = 1,
        fechaUltimaSesion = null,
        consentimientoRevocado = revocado
    )

    @Test
    fun `si la lista no cambia no se borra nada`() {
        val lista = listOf(paciente("a"), paciente("b"))
        assertTrue(pacientesAOlvidar(anteriores = lista, actuales = lista).isEmpty())
    }

    @Test
    fun `quien deja de estar a cargo se olvida`() {
        val olvidar = pacientesAOlvidar(
            anteriores = listOf(paciente("a"), paciente("b")),
            actuales = listOf(paciente("a"))
        )
        assertEquals(setOf("b"), olvidar)
    }

    @Test
    fun `quien revoco el consentimiento se olvida aunque siga en la lista`() {
        val olvidar = pacientesAOlvidar(
            anteriores = listOf(paciente("a"), paciente("b")),
            actuales = listOf(paciente("a", revocado = true), paciente("b"))
        )
        assertEquals(setOf("a"), olvidar)
    }

    @Test
    fun `un paciente nuevo no se olvida`() {
        val olvidar = pacientesAOlvidar(
            anteriores = emptyList(),
            actuales = listOf(paciente("a"))
        )
        assertTrue(olvidar.isEmpty())
    }
}
