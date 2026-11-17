package com.steadypath.app.domain.comparison

import com.steadypath.app.domain.model.KeypointType
import org.junit.Test

class ExerciseReferenceTest {

    private val definition = ExerciseDefinition(
        exerciseId = "ex",
        angleDefinitions = listOf(
            AngleDefinition("codo", KeypointType.LEFT_SHOULDER, KeypointType.LEFT_ELBOW, KeypointType.LEFT_WRIST)
        ),
        driverAngleIndex = 0
    )

    @Test(expected = IllegalArgumentException::class)
    fun `trayectoria vacia falla fuerte`() {
        ExerciseReference(
            definition = definition,
            trajectory = emptyList(),
            metadata = ReferenceMetadata(fps = 20f, version = "1", source = "test"),
            scoring = ScoringConfig(similarityScale = 0.5, goodThreshold = 0.7f)
        )
    }
}
