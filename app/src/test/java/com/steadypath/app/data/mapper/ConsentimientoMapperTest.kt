package com.steadypath.app.data.mapper

import com.steadypath.app.data.dto.PatientSummaryDto
import com.steadypath.app.data.dto.UserResponseDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El estado del consentimiento (RF-48) llega como fecha en /me y como booleano en la
 * lista del profesional. El resto de la app solo mira el booleano del dominio.
 */
class ConsentimientoMapperTest {

    private fun usuario(revocadoEn: String?) = UserResponseDto(
        idUsuario = "id",
        nombre = "María",
        email = "maria@test.com",
        rol = "Paciente",
        fechaRegistro = "2026-10-01T00:00:00Z",
        tipoProfesional = null,
        matricula = null,
        institucion = null,
        consentimientoRevocadoEn = revocadoEn
    )

    @Test
    fun `con fecha de revocacion el usuario queda con el consentimiento revocado`() {
        assertTrue(usuario("2026-10-16T14:00:00Z").toDomain().consentimientoRevocado)
    }

    @Test
    fun `sin fecha el consentimiento esta vigente`() {
        assertFalse(usuario(null).toDomain().consentimientoRevocado)
    }

    @Test
    fun `la lista del profesional conserva la marca de revocado`() {
        val dto = PatientSummaryDto(
            idPaciente = "id",
            nombre = "María",
            email = "maria@test.com",
            edad = 65,
            cantidadRutinasActivas = 0,
            fechaUltimaSesion = null,
            consentimientoRevocado = true
        )
        assertTrue(dto.toDomain().consentimientoRevocado)
    }
}
