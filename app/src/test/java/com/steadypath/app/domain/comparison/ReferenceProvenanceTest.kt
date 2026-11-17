package com.steadypath.app.domain.comparison

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceProvenanceTest {

    @Test
    fun `solo la referencia del profesional respalda un juicio de correccion`() {
        assertTrue(ReferenceProvenance.PROFESIONAL.respaldaCorreccion)
        assertFalse(ReferenceProvenance.PROPIA.respaldaCorreccion)
        assertFalse(ReferenceProvenance.EMPAQUETADA.respaldaCorreccion)
    }
}
