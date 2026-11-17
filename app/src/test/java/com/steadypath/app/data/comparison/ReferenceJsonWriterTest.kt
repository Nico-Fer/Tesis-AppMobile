package com.steadypath.app.data.comparison

import com.steadypath.app.domain.comparison.AngleFrame
import com.steadypath.app.domain.comparison.ExerciseReference
import com.steadypath.app.domain.comparison.MovementPatternCatalog
import com.steadypath.app.domain.comparison.ReferenceMetadata
import com.steadypath.app.domain.comparison.ScoringConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceJsonWriterTest {

    private fun reference(
        trajectory: List<AngleFrame> = List(5) { i ->
            AngleFrame(floatArrayOf(0.5f + i * 0.1f, 1f), booleanArrayOf(true, true), i * 50L)
        }
    ): ExerciseReference {
        val pattern = MovementPatternCatalog.bySlug("flexion_rodilla_izq")!!
        return ExerciseReference(
            definition = pattern.toExerciseDefinition("11111111-2222-3333-4444-555555555555"),
            trajectory = trajectory,
            metadata = ReferenceMetadata(fps = 20f, version = "1.0", source = "captura-in-app"),
            scoring = ScoringConfig(similarityScale = 0.5, goodThreshold = 0.7f)
        )
    }

    @Test
    fun `round-trip write-parse preserva la referencia`() {
        val original = reference()
        val parsed = ReferenceJsonParser.parse(ReferenceJsonWriter.write(original))

        assertEquals(original.definition, parsed.definition)
        assertEquals(original.metadata, parsed.metadata)
        assertEquals(original.scoring, parsed.scoring)
        assertEquals(original.trajectory.size, parsed.trajectory.size)
        original.trajectory.zip(parsed.trajectory).forEachIndexed { i, (a, b) ->
            assertEquals("trajectory[$i]", a.angles.toList(), b.angles.toList())
            assertTrue(b.valid.all { it })
        }
    }

    @Test
    fun `los timestamps reconstruidos siguen el fps declarado`() {
        val parsed = ReferenceJsonParser.parse(ReferenceJsonWriter.write(reference()))
        // 20 fps → 50 ms por frame, reconstruidos desde el índice.
        assertEquals(listOf(0L, 50L, 100L, 150L, 200L), parsed.trajectory.map { it.timestampMs })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `frame con dimension invalida no se puede exportar`() {
        val trajectory = listOf(
            AngleFrame(floatArrayOf(0.5f, 1f), booleanArrayOf(true, false), 0L)
        )
        ReferenceJsonWriter.write(reference(trajectory))
    }
}
