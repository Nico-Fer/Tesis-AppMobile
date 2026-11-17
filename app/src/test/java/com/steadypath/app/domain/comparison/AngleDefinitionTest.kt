package com.steadypath.app.domain.comparison

import com.steadypath.app.domain.model.KeypointType
import org.junit.Assert.assertEquals
import org.junit.Test

class AngleDefinitionTest {

    private fun codo(weight: Float = 1f) = AngleDefinition(
        name = "codo_izq",
        pointA = KeypointType.LEFT_SHOULDER,
        vertex = KeypointType.LEFT_ELBOW,
        pointC = KeypointType.LEFT_WRIST,
        weight = weight
    )

    // ── AngleDefinition ──────────────────────────────────────────────────────

    @Test
    fun `peso por defecto es uno`() {
        assertEquals(1f, codo().weight, 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `peso cero falla fuerte`() {
        codo(weight = 0f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `peso negativo falla fuerte`() {
        codo(weight = -0.5f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `extremo A igual al vertice es degenerado`() {
        AngleDefinition("x", KeypointType.LEFT_ELBOW, KeypointType.LEFT_ELBOW, KeypointType.LEFT_WRIST)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `extremo C igual al vertice es degenerado`() {
        AngleDefinition("x", KeypointType.LEFT_SHOULDER, KeypointType.LEFT_ELBOW, KeypointType.LEFT_ELBOW)
    }

    // ── ExerciseDefinition ───────────────────────────────────────────────────

    @Test
    fun `dimensions es la cantidad de angulos`() {
        val def = ExerciseDefinition("ex", listOf(codo(), codo(0.5f)), driverAngleIndex = 1)
        assertEquals(2, def.dimensions)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ejercicio sin angulos falla fuerte`() {
        ExerciseDefinition("ex", emptyList(), driverAngleIndex = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `driverAngleIndex negativo falla fuerte`() {
        ExerciseDefinition("ex", listOf(codo()), driverAngleIndex = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `driverAngleIndex igual a la cantidad de angulos falla fuerte`() {
        ExerciseDefinition("ex", listOf(codo()), driverAngleIndex = 1)
    }
}
