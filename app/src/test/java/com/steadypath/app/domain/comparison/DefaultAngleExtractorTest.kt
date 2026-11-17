package com.steadypath.app.domain.comparison

import com.steadypath.app.domain.model.Keypoint
import com.steadypath.app.domain.model.KeypointType
import com.steadypath.app.domain.model.PoseFrame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class DefaultAngleExtractorTest {

    private val extractor = DefaultAngleExtractor(confidenceThreshold = 0.5f)

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Construye un PoseFrame cuadrado (100×100) con los 17 keypoints en (0,0) conf=1,
     * sobreescribiendo los indicados con posiciones EN PÍXELES.
     */
    private fun poseFrame(
        overrides: Map<KeypointType, Triple<Float, Float, Float>>,
        sourceWidth: Int = 100,
        sourceHeight: Int = 100,
        timestampMs: Long = 42L
    ): PoseFrame {
        val keypoints = KeypointType.entriesByIndex.map { type ->
            val (px, py, conf) = overrides[type] ?: Triple(0f, 0f, 1f)
            Keypoint(
                type = type,
                x = px / sourceWidth,
                y = py / sourceHeight,
                confidence = conf
            )
        }
        return PoseFrame(
            keypoints = keypoints,
            score = 0.9f,
            timestampMs = timestampMs,
            sourceWidth = sourceWidth,
            sourceHeight = sourceHeight
        )
    }

    private fun elbowDefinition(weight: Float = 1f) = ExerciseDefinition(
        exerciseId = "test",
        angleDefinitions = listOf(
            AngleDefinition(
                name = "codo_izq",
                pointA = KeypointType.LEFT_SHOULDER,
                vertex = KeypointType.LEFT_ELBOW,
                pointC = KeypointType.LEFT_WRIST,
                weight = weight
            )
        ),
        driverAngleIndex = 0
    )

    private val eps = 1e-4f

    // ── Ángulos conocidos ────────────────────────────────────────────────────

    @Test
    fun `angulo recto devuelve pi medios`() {
        // Hombro a la derecha del codo, muñeca debajo del codo → vectores ortogonales.
        val frame = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(50f, 50f, 1f),
                KeypointType.LEFT_SHOULDER to Triple(70f, 50f, 1f),
                KeypointType.LEFT_WRIST to Triple(50f, 70f, 1f)
            )
        )
        val result = extractor.extract(frame, elbowDefinition())

        assertTrue(result.valid[0])
        assertEquals(PI.toFloat() / 2f, result.angles[0], eps)
    }

    @Test
    fun `brazo extendido devuelve pi`() {
        // Hombro, codo y muñeca colineales con el codo en el medio.
        val frame = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(50f, 50f, 1f),
                KeypointType.LEFT_SHOULDER to Triple(30f, 50f, 1f),
                KeypointType.LEFT_WRIST to Triple(70f, 50f, 1f)
            )
        )
        val result = extractor.extract(frame, elbowDefinition())

        assertTrue(result.valid[0])
        assertEquals(PI.toFloat(), abs(result.angles[0]), eps)
    }

    @Test
    fun `angulo cerrado de 45 grados`() {
        val frame = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(50f, 50f, 1f),
                KeypointType.LEFT_SHOULDER to Triple(70f, 50f, 1f),
                KeypointType.LEFT_WRIST to Triple(70f, 70f, 1f)
            )
        )
        val result = extractor.extract(frame, elbowDefinition())

        assertTrue(result.valid[0])
        assertEquals(PI.toFloat() / 4f, result.angles[0], eps)
    }

    @Test
    fun `resultado siempre en rango menos pi a pi`() {
        // Configuración con cross negativo → ángulo signado negativo, no 3π/2.
        val frame = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(50f, 50f, 1f),
                KeypointType.LEFT_SHOULDER to Triple(70f, 50f, 1f),
                KeypointType.LEFT_WRIST to Triple(50f, 30f, 1f)
            )
        )
        val result = extractor.extract(frame, elbowDefinition())

        assertTrue(result.valid[0])
        assertEquals(-PI.toFloat() / 2f, result.angles[0], eps)
        assertTrue(result.angles[0] > -PI.toFloat() - eps && result.angles[0] <= PI.toFloat() + eps)
    }

    // ── Corrección de aspect ratio ───────────────────────────────────────────

    @Test
    fun `imagen no cuadrada no deforma el angulo`() {
        // Mismo ángulo recto en píxeles, pero imagen 200×100: si el extractor operara en
        // coordenadas normalizadas el ángulo saldría distorsionado.
        val frame = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(100f, 50f, 1f),
                KeypointType.LEFT_SHOULDER to Triple(140f, 50f, 1f),
                KeypointType.LEFT_WRIST to Triple(100f, 90f, 1f)
            ),
            sourceWidth = 200,
            sourceHeight = 100
        )
        val result = extractor.extract(frame, elbowDefinition())

        assertTrue(result.valid[0])
        assertEquals(PI.toFloat() / 2f, result.angles[0], eps)
    }

    // ── Enmascarado por confianza ────────────────────────────────────────────

    @Test
    fun `keypoint con baja confianza invalida la dimension`() {
        val frame = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(50f, 50f, 1f),
                KeypointType.LEFT_SHOULDER to Triple(70f, 50f, 0.3f), // bajo umbral
                KeypointType.LEFT_WRIST to Triple(50f, 70f, 1f)
            )
        )
        val result = extractor.extract(frame, elbowDefinition())

        assertFalse(result.valid[0])
        assertFalse(result.hasValidDimensions())
    }

    @Test
    fun `solo se invalida la dimension afectada`() {
        val definition = ExerciseDefinition(
            exerciseId = "test",
            angleDefinitions = listOf(
                AngleDefinition(
                    name = "codo_izq",
                    pointA = KeypointType.LEFT_SHOULDER,
                    vertex = KeypointType.LEFT_ELBOW,
                    pointC = KeypointType.LEFT_WRIST
                ),
                AngleDefinition(
                    name = "codo_der",
                    pointA = KeypointType.RIGHT_SHOULDER,
                    vertex = KeypointType.RIGHT_ELBOW,
                    pointC = KeypointType.RIGHT_WRIST
                )
            ),
            driverAngleIndex = 0
        )
        val frame = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(50f, 50f, 1f),
                KeypointType.LEFT_SHOULDER to Triple(70f, 50f, 1f),
                KeypointType.LEFT_WRIST to Triple(50f, 70f, 1f),
                KeypointType.RIGHT_ELBOW to Triple(20f, 20f, 1f),
                KeypointType.RIGHT_SHOULDER to Triple(30f, 20f, 0.1f), // bajo umbral
                KeypointType.RIGHT_WRIST to Triple(20f, 30f, 1f)
            )
        )
        val result = extractor.extract(frame, definition)

        assertTrue(result.valid[0])
        assertFalse(result.valid[1])
        assertTrue(result.hasValidDimensions())
    }

    // ── Casos degenerados ────────────────────────────────────────────────────

    @Test
    fun `keypoints superpuestos invalidan la dimension`() {
        // Muñeca exactamente sobre el codo → vector de largo cero.
        val frame = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(50f, 50f, 1f),
                KeypointType.LEFT_SHOULDER to Triple(70f, 50f, 1f),
                KeypointType.LEFT_WRIST to Triple(50f, 50f, 1f)
            )
        )
        val result = extractor.extract(frame, elbowDefinition())

        assertFalse(result.valid[0])
    }

    @Test
    fun `extremo A superpuesto al vertice tambien invalida la dimension`() {
        val frame = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(50f, 50f, 1f),
                KeypointType.LEFT_SHOULDER to Triple(50f, 50f, 1f),
                KeypointType.LEFT_WRIST to Triple(50f, 70f, 1f)
            )
        )
        val result = extractor.extract(frame, elbowDefinition())

        assertFalse(result.valid[0])
    }

    @Test
    fun `baja confianza en el vertice o en el extremo C invalida la dimension`() {
        val lowVertex = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(50f, 50f, 0.2f),
                KeypointType.LEFT_SHOULDER to Triple(70f, 50f, 1f),
                KeypointType.LEFT_WRIST to Triple(50f, 70f, 1f)
            )
        )
        val lowC = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(50f, 50f, 1f),
                KeypointType.LEFT_SHOULDER to Triple(70f, 50f, 1f),
                KeypointType.LEFT_WRIST to Triple(50f, 70f, 0.2f)
            )
        )

        assertFalse(extractor.extract(lowVertex, elbowDefinition()).valid[0])
        assertFalse(extractor.extract(lowC, elbowDefinition()).valid[0])
    }

    @Test
    fun `frame con menos keypoints que los que pide la terna invalida la dimension`() {
        // Un modelo que emita menos de 17 keypoints no debe romper: la dimensión queda inválida.
        val full = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(50f, 50f, 1f),
                KeypointType.LEFT_SHOULDER to Triple(70f, 50f, 1f),
                KeypointType.LEFT_WRIST to Triple(50f, 70f, 1f)
            )
        )
        val truncated = PoseFrame(
            keypoints = full.keypoints.take(KeypointType.LEFT_WRIST.ordinal), // sin la muñeca
            score = full.score,
            timestampMs = full.timestampMs,
            sourceWidth = full.sourceWidth,
            sourceHeight = full.sourceHeight
        )
        val result = extractor.extract(truncated, elbowDefinition())

        assertFalse(result.valid[0])
        assertEquals(1, result.angles.size)
    }

    // ── Metadata ─────────────────────────────────────────────────────────────

    @Test
    fun `propaga el timestamp del frame`() {
        val frame = poseFrame(
            mapOf(
                KeypointType.LEFT_ELBOW to Triple(50f, 50f, 1f),
                KeypointType.LEFT_SHOULDER to Triple(70f, 50f, 1f),
                KeypointType.LEFT_WRIST to Triple(50f, 70f, 1f)
            ),
            timestampMs = 12345L
        )
        val result = extractor.extract(frame, elbowDefinition())

        assertEquals(12345L, result.timestampMs)
    }
}
