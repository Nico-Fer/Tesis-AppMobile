package com.steadypath.app.domain.comparison

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class ReferenceAlignmentTest {

    private fun frame(driver: Float, ts: Long, valid: Boolean = true) = AngleFrame(
        angles = floatArrayOf(driver),
        valid = booleanArrayOf(valid),
        timestampMs = ts
    )

    /** Trayectoria anclada al reposo (peak-first): 2.85 → 2.45 → 2.85, valle en el medio. */
    private fun restAnchored(): List<AngleFrame> {
        val values = (0..10).map { 2.85f - 0.4f * it / 10f } + (1..10).map { 2.45f + 0.4f * it / 10f }
        return values.mapIndexed { i, v -> frame(v, i * 50L) }
    }

    // ── profileOf ────────────────────────────────────────────────────────────

    @Test
    fun `amplitud y valle de una trayectoria peak-first`() {
        val profile = ReferenceAlignment.profileOf(restAnchored(), driverAngleIndex = 0)

        assertEquals(0.4f, profile.amplitudeRad, 1e-4f)
        assertEquals(10, profile.valleyIndex)
    }

    @Test
    fun `trayectoria valley-first ya esta anclada`() {
        val values = (0..10).map { it / 10f } + (1..10).map { (10 - it) / 10f }
        val profile = ReferenceAlignment.profileOf(
            values.mapIndexed { i, v -> frame(v, i * 50L) },
            driverAngleIndex = 0
        )

        assertEquals(0, profile.valleyIndex)
        assertEquals(1f, profile.amplitudeRad, 1e-4f)
    }

    @Test
    fun `la amplitud se desenvuelve al cruzar mas menos pi`() {
        // 3.0 → 3.3 rad: envuelto se ve como 3.0 → −2.98; la excursión real es 0.3, no ~6.
        val pi = PI.toFloat()
        fun wrap(v: Float): Float = if (v > pi) v - 2f * pi else v
        val values = (0..10).map { wrap(3.0f + 0.3f * it / 10f) } + (1..10).map { wrap(3.3f - 0.3f * it / 10f) }
        val profile = ReferenceAlignment.profileOf(
            values.mapIndexed { i, v -> frame(v, i * 50L) },
            driverAngleIndex = 0
        )

        assertEquals(0.3f, profile.amplitudeRad, 1e-3f)
        assertEquals(0, profile.valleyIndex)
    }

    @Test
    fun `cuadros con conductor invalido no participan del perfil`() {
        val trajectory = restAnchored().toMutableList()
        // Outlier inválido más profundo que el valle real: no debe ganar.
        trajectory.add(5, frame(-99f, 260L, valid = false))
        val profile = ReferenceAlignment.profileOf(trajectory, driverAngleIndex = 0)

        assertEquals(0.4f, profile.amplitudeRad, 1e-4f)
        assertEquals(11, profile.valleyIndex) // el valle real quedó corrido por la inserción
    }

    // ── alignToDriverValley ──────────────────────────────────────────────────

    @Test
    fun `rota la trayectoria para arrancar en el valle`() {
        val original = restAnchored()
        val rotated = ReferenceAlignment.alignToDriverValley(original, valleyIndex = 10)

        assertEquals(original.size, rotated.size)
        assertEquals(2.45f, rotated.first().angles[0], 1e-4f)
        // Mismo multiconjunto de ángulos, solo reordenado.
        assertEquals(
            original.map { it.angles[0] }.sorted(),
            rotated.map { it.angles[0] }.sorted()
        )
        // Timestamps re-estampados monótonos.
        rotated.zipWithNext { a, b -> assertTrue(a.timestampMs < b.timestampMs) }
    }

    @Test
    fun `valleyIndex cero es un no-op`() {
        val original = restAnchored()
        assertSame(original, ReferenceAlignment.alignToDriverValley(original, valleyIndex = 0))
    }

    // ── toStartDeviation ─────────────────────────────────────────────

    @Test
    fun `toStartDeviation elimina el offset postural`() {
        val original = restAnchored()
        val shifted = original.map {
            AngleFrame(floatArrayOf(it.angles[0] + 0.5f), it.valid, it.timestampMs)
        }
        val a = ReferenceAlignment.toStartDeviation(original).map { it.angles[0] }
        val b = ReferenceAlignment.toStartDeviation(shifted).map { it.angles[0] }

        assertEquals(0f, a.first(), 1e-6f)
        a.zip(b).forEach { (x, y) -> assertEquals(x, y, 1e-4f) }
    }

    @Test
    fun `toStartDeviation toma la linea base del primer cuadro valido`() {
        val frames = listOf(
            frame(99f, 0L, valid = false),
            frame(2.8f, 50L),
            frame(2.9f, 100L)
        )
        val out = ReferenceAlignment.toStartDeviation(frames)

        assertEquals(0f, out[1].angles[0], 1e-6f)
        assertEquals(0.1f, out[2].angles[0], 1e-4f)
    }

    @Test
    fun `toStartDeviation con lista vacia devuelve la misma lista`() {
        val empty = emptyList<AngleFrame>()
        assertSame(empty, ReferenceAlignment.toStartDeviation(empty))
    }

    @Test
    fun `toStartDeviation deja intacta una dimension que nunca fue valida`() {
        // Sin línea base no hay excursión que medir: el ángulo crudo pasa tal cual.
        val frames = listOf(
            AngleFrame(floatArrayOf(1.0f, 7f), booleanArrayOf(true, false), 0L),
            AngleFrame(floatArrayOf(1.2f, 8f), booleanArrayOf(true, false), 50L)
        )
        val out = ReferenceAlignment.toStartDeviation(frames)

        assertEquals(0.2f, out[1].angles[0], 1e-4f)
        assertEquals(7f, out[0].angles[1], 0f)
        assertEquals(8f, out[1].angles[1], 0f)
    }

    // ── Bordes ──────────────────────────────────────────────────────────────────

    @Test
    fun `trayectoria de un solo cuadro no se rota`() {
        val single = listOf(frame(1f, 0L))
        assertSame(single, ReferenceAlignment.alignToDriverValley(single, valleyIndex = 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valleyIndex fuera de rango falla fuerte`() {
        ReferenceAlignment.alignToDriverValley(restAnchored(), valleyIndex = 21)
    }

    @Test
    fun `conductor fuera del vector de angulos deja el perfil vacio`() {
        val profile = ReferenceAlignment.profileOf(restAnchored(), driverAngleIndex = 5)

        assertEquals(0f, profile.amplitudeRad, 0f)
        assertEquals(0, profile.valleyIndex)
        assertTrue(ReferenceAlignment.unwrappedDriver(restAnchored(), 5).all { it.isNaN() })
    }

    @Test
    fun `trayectoria sin ningun conductor valido tiene amplitud cero`() {
        val frames = listOf(frame(1f, 0L, valid = false), frame(2f, 50L, valid = false))
        val profile = ReferenceAlignment.profileOf(frames, driverAngleIndex = 0)

        assertEquals(0f, profile.amplitudeRad, 0f)
        assertEquals(0, profile.valleyIndex)
    }
}
