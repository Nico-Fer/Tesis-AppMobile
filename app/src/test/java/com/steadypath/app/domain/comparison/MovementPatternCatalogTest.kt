package com.steadypath.app.domain.comparison

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovementPatternCatalogTest {

    @Test
    fun `el catalogo cubre los cuatro ejercicios del alcance`() {
        val slugs = MovementPatternCatalog.patterns.map { it.slug }
        assertTrue(slugs.contains("levantamiento_brazos"))
        assertTrue(slugs.contains("flexion_rodilla_izq"))
        assertTrue(slugs.contains("flexion_rodilla_der"))
        assertTrue(slugs.contains("sentarse_levantarse_silla"))
        assertTrue(slugs.contains("sentadilla_parcial"))
        assertTrue(slugs.contains("elevacion_talones_izq"))
        assertTrue(slugs.contains("extension_pierna_sentada_izq"))
        assertTrue(slugs.contains("extension_pierna_sentada_der"))
    }

    @Test
    fun `los slugs son unicos`() {
        val slugs = MovementPatternCatalog.patterns.map { it.slug }
        assertEquals(slugs.size, slugs.toSet().size)
    }

    @Test
    fun `todos los patrones producen una ExerciseDefinition valida`() {
        // El init de ExerciseDefinition valida driver en rango y lista no vacía.
        MovementPatternCatalog.patterns.forEach { pattern ->
            val definition = pattern.toExerciseDefinition("un-uuid-cualquiera")
            assertEquals("un-uuid-cualquiera", definition.exerciseId)
            assertEquals(pattern.angleDefinitions.size, definition.dimensions)
        }
    }

    @Test
    fun `los nombres de angulo dentro de un patron son unicos`() {
        MovementPatternCatalog.patterns.forEach { pattern ->
            val names = pattern.angleDefinitions.map { it.name }
            assertEquals("Nombres duplicados en '${pattern.slug}'", names.size, names.toSet().size)
        }
    }

    @Test
    fun `bySlug encuentra patrones existentes y devuelve null para desconocidos`() {
        assertNotNull(MovementPatternCatalog.bySlug("levantamiento_brazos"))
        assertNull(MovementPatternCatalog.bySlug("inexistente"))
    }
}
