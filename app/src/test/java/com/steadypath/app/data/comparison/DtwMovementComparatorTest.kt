package com.steadypath.app.data.comparison

import com.steadypath.app.domain.comparison.AngleDefinition
import com.steadypath.app.domain.comparison.AngleFrame
import com.steadypath.app.domain.comparison.DefaultAngleExtractor
import com.steadypath.app.domain.comparison.ErrorDirection
import com.steadypath.app.domain.comparison.ExerciseDefinition
import com.steadypath.app.domain.comparison.ExerciseReference
import com.steadypath.app.domain.comparison.MovementStatus
import com.steadypath.app.domain.comparison.PeakValleyRepetitionSegmenter
import com.steadypath.app.domain.comparison.ReferenceMetadata
import com.steadypath.app.domain.comparison.RepetitionSegmenterFactory
import com.steadypath.app.domain.comparison.ScoringConfig
import com.steadypath.app.domain.model.Keypoint
import com.steadypath.app.domain.model.KeypointType
import com.steadypath.app.domain.model.PatientTolerance
import com.steadypath.app.domain.model.PoseFrame
import com.steadypath.app.domain.repository.PatientToleranceRepository
import com.steadypath.app.domain.comparison.ReferenceProvenance
import com.steadypath.app.domain.repository.ReferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class DtwMovementComparatorTest {

    // ── Fixture ──────────────────────────────────────────────────────────────

    private val definition = ExerciseDefinition(
        exerciseId = "ex-1",
        angleDefinitions = listOf(
            AngleDefinition(
                name = "codo_izq",
                pointA = KeypointType.LEFT_SHOULDER,
                vertex = KeypointType.LEFT_ELBOW,
                pointC = KeypointType.LEFT_WRIST
            )
        ),
        driverAngleIndex = 0
    )

    /** Triángulo 0 → [peak] → 0 en 21 valores (una repetición valle→pico→valle). */
    private fun triangleValues(peak: Float): List<Float> =
        (0..10).map { peak * it / 10f } + (1..10).map { peak * (10 - it) / 10f }

    private fun reference(peak: Float = 1.4f) = ExerciseReference(
        definition = definition,
        trajectory = triangleValues(peak).mapIndexed { i, v ->
            AngleFrame(floatArrayOf(v), booleanArrayOf(true), i * 50L)
        },
        metadata = ReferenceMetadata(fps = 20f, version = "1.0", source = "test"),
        scoring = ScoringConfig(similarityScale = 0.5, goodThreshold = 0.7f)
    )

    /**
     * PoseFrame cuyo ángulo de codo izquierdo vale exactamente [theta]: codo en el centro,
     * hombro sobre +x y muñeca rotada [theta] alrededor del codo.
     */
    private fun poseWithElbowAngle(theta: Float, ts: Long, confidence: Float = 1f): PoseFrame {
        val keypoints = KeypointType.entriesByIndex.map { type ->
            val (x, y) = when (type) {
                KeypointType.LEFT_ELBOW -> 0.5f to 0.5f
                KeypointType.LEFT_SHOULDER -> 0.7f to 0.5f
                KeypointType.LEFT_WRIST -> (0.5f + 0.2f * cos(theta)) to (0.5f + 0.2f * sin(theta))
                else -> 0.1f to 0.1f
            }
            Keypoint(type = type, x = x, y = y, confidence = confidence)
        }
        return PoseFrame(
            keypoints = keypoints,
            score = 0.9f,
            timestampMs = ts,
            sourceWidth = 100,
            sourceHeight = 100
        )
    }

    /** [reps] repeticiones limpias del triángulo + cola para cerrar la última. */
    private fun cleanSignal(reps: Int, peak: Float = 1.4f, offset: Float = 0f): List<PoseFrame> {
        val frames = ArrayList<PoseFrame>()
        var ts = 0L
        frames.add(poseWithElbowAngle(offset, ts))
        repeat(reps) {
            for (v in triangleValues(peak).drop(1)) {
                ts += 100
                frames.add(poseWithElbowAngle(v + offset, ts))
            }
        }
        for (i in 1..5) {
            ts += 100
            frames.add(poseWithElbowAngle(peak * i / 10f + offset, ts))
        }
        return frames
    }

    private fun comparator(
        repository: ReferenceRepository,
        tolerance: PatientTolerance? = null
    ) = DtwMovementComparator(
        referenceRepository = repository,
        angleExtractor = DefaultAngleExtractor(),
        segmenterFactory = RepetitionSegmenterFactory { index, amplitude ->
            PeakValleyRepetitionSegmenter.tunedFor(index, amplitude)
        },
        toleranceRepository = FakeToleranceRepository(tolerance),
        computeDispatcher = Dispatchers.Unconfined
    )

    private class FakeRepository(private val reference: ExerciseReference?) : ReferenceRepository {
        override suspend fun provenanceOf(exerciseId: String) = ReferenceProvenance.EMPAQUETADA

        override suspend fun getReference(exerciseId: String) = reference
    }

    /** Sin tolerancia por default: preserva el comportamiento de amplitud-de-referencia. */
    private class FakeToleranceRepository(
        private val tolerance: PatientTolerance?
    ) : PatientToleranceRepository {
        override suspend fun getOwnTolerance(idEjercicio: String): Result<PatientTolerance> =
            Result.success(tolerance ?: PatientTolerance(tieneCalibracion = false, null, null))
    }

    // ── Camino feliz ─────────────────────────────────────────────────────────

    @Test
    fun `ejecucion correcta emite un resultado Good por repeticion`() = runBlocking {
        val results = comparator(FakeRepository(reference()))
            .compare("ex-1", cleanSignal(reps = 3).asFlow())
            .toList()

        assertEquals(3, results.size)
        // El primer segmento es parcial (arranca en el reposo inicial): cuenta y se
        // muestra como correcta, sin score.
        assertEquals(MovementStatus.Good, results[0].status)
        assertEquals(null, results[0].similarityScore)
        assertEquals(1, results[0].repDelta)
        assertEquals(1, results[0].repIndex)
        results.drop(1).forEachIndexed { i, result ->
            assertEquals(1, result.repDelta)
            assertEquals(i + 2, result.repIndex)
            assertEquals(MovementStatus.Good, result.status)
            assertTrue(
                "Similitud esperada alta, fue ${result.similarityScore}",
                result.similarityScore!! > 0.9f
            )
            assertTrue(result.normalizedDistance!! < 0.06)
        }
    }

    @Test
    fun `movimiento con amplitud claramente insuficiente emite NeedsCorrection`() = runBlocking {
        val results = comparator(FakeRepository(reference(peak = 1.4f)))
            .compare("ex-1", cleanSignal(reps = 3, peak = 0.4f).asFlow())
            .toList()

        assertEquals(3, results.size)
        for (result in results.drop(1)) {
            assertEquals(MovementStatus.NeedsCorrection, result.status)
            assertTrue(result.similarityScore!! < 0.7f)
        }
    }

    @Test
    fun `amplitud moderadamente insuficiente tambien emite NeedsCorrection gracias al shape score`() = runBlocking {
        // Caso que motivó el NCC: con pico 0.7 vs 1.4 el warping del DTW encontraba
        // alineamientos baratos y la rep pasaba como Good. El ratio de amplitud del
        // shapeScore (~0.5) lo castiga.
        val results = comparator(FakeRepository(reference(peak = 1.4f)))
            .compare("ex-1", cleanSignal(reps = 3, peak = 0.7f).asFlow())
            .toList()

        assertEquals(3, results.size)
        for (result in results.drop(1)) {
            assertEquals(MovementStatus.NeedsCorrection, result.status)
            assertTrue("shapeScore=${result.shapeScore}", result.shapeScore!! < 0.7f)
        }
    }

    @Test
    fun `una rep con amplitud corta trae hint UNDERSHOOT del angulo conductor`() = runBlocking {
        val results = comparator(FakeRepository(reference(peak = 1.4f)))
            .compare("ex-1", cleanSignal(reps = 2, peak = 0.4f).asFlow())
            .toList()

        val hints = results.last().hints
        assertTrue("Esperaba hints, no vino ninguno", hints.isNotEmpty())
        assertEquals("codo_izq", hints.first().angleName)
        assertEquals(ErrorDirection.UNDERSHOOT, hints.first().direction)
    }

    @Test
    fun `la similitud ordena ejecuciones correctas sobre incorrectas`() = runBlocking {
        val good = comparator(FakeRepository(reference()))
            .compare("ex-1", cleanSignal(reps = 2).asFlow())
            .toList()
        val bad = comparator(FakeRepository(reference()))
            .compare("ex-1", cleanSignal(reps = 2, peak = 0.4f).asFlow())
            .toList()

        assertTrue(
            "good=${good.last().similarityScore} debe superar bad=${bad.last().similarityScore}",
            good.last().similarityScore!! > bad.last().similarityScore!!
        )
    }

    // ── Referencias peak-first (autoradas offline, ancladas al reposo) ───────

    /** Referencia anclada al reposo: [rest] → [rest]−[dip] → [rest] (sentadilla-like). */
    private fun restAnchoredReference(rest: Float = 1.4f, dip: Float = 1.4f) = ExerciseReference(
        definition = definition,
        trajectory = ((0..10).map { rest - dip * it / 10f } + (1..10).map { rest - dip * (10 - it) / 10f })
            .mapIndexed { i, v -> AngleFrame(floatArrayOf(v), booleanArrayOf(true), i * 50L) },
        metadata = ReferenceMetadata(fps = 20f, version = "1.0", source = "test"),
        scoring = ScoringConfig(similarityScale = 0.5, goodThreshold = 0.7f)
    )

    /** Ejecución peak-first: arranca en reposo [rest] y baja [dip] por rep, sin cola. */
    private fun peakFirstSignal(reps: Int, rest: Float = 1.4f, dip: Float = 1.4f): List<PoseFrame> {
        val frames = ArrayList<PoseFrame>()
        var ts = 0L
        frames.add(poseWithElbowAngle(rest, ts))
        repeat(reps) {
            for (i in 1..10) { ts += 100; frames.add(poseWithElbowAngle(rest - dip * i / 10f, ts)) }
            for (i in 1..10) { ts += 100; frames.add(poseWithElbowAngle(rest - dip * (10 - i) / 10f, ts)) }
        }
        return frames
    }

    @Test
    fun `referencia anclada al reposo se rota y las reps peak-first puntuan Good`() = runBlocking {
        val results = comparator(FakeRepository(restAnchoredReference()))
            .compare("ex-1", peakFirstSignal(reps = 3).asFlow())
            .toList()

        // N dips → N reps, incluida la última (la vuelta al reposo cierra el segmento).
        assertEquals(3, results.size)
        assertEquals(MovementStatus.Good, results[0].status)
        assertEquals(null, results[0].similarityScore)
        for (result in results.drop(1)) {
            assertEquals(MovementStatus.Good, result.status)
            assertTrue(
                "Sin rotación esto compararía ciclos en contrafase; fue ${result.similarityScore}",
                result.similarityScore!! > 0.85f
            )
        }
    }

    @Test
    fun `sentadilla parcial apenas mas superficial que la referencia sigue contando`() = runBlocking {
        // Amplitud referencia 0.4 rad; paciente a 0.3: el default fijo (0.35) daría 0 reps.
        val results = comparator(FakeRepository(restAnchoredReference(rest = 2.85f, dip = 0.4f)))
            .compare("ex-1", peakFirstSignal(reps = 3, rest = 2.85f, dip = 0.3f).asFlow())
            .toList()

        assertEquals(3, results.size)
        assertTrue(results.all { it.repDelta == 1 })
    }

    @Test
    fun `offset postural constante no penaliza porque se compara la excursion`() = runBlocking {
        // Silla/cámara distintas a la referencia: mismo movimiento, línea base corrida 0.5 rad.
        val results = comparator(FakeRepository(reference()))
            .compare("ex-1", cleanSignal(reps = 3, offset = 0.5f).asFlow())
            .toList()

        assertEquals(3, results.size)
        for (result in results.drop(1)) {
            assertEquals(MovementStatus.Good, result.status)
            assertTrue("similitud=${result.similarityScore}", result.similarityScore!! > 0.85f)
        }
    }

    // ── Tolerancia propia calibrada ──────────────────────────────────────────

    @Test
    fun `tolerancia propia calibrada reemplaza la amplitud de referencia para contar reps`() = runBlocking {
        // Referencia con pico 1.4 rad: el piso de prominencia que deriva (0.35 rad) rechaza
        // un movimiento de 0.3 rad, muy por debajo de la amplitud "ideal" de la referencia.
        val withoutCalibration = comparator(FakeRepository(reference(peak = 1.4f)))
            .compare("ex-1", cleanSignal(reps = 3, peak = 0.3f).asFlow())
            .toList()
        assertTrue(
            "Sin calibración no debería contar reps tan por debajo de la referencia",
            withoutCalibration.isEmpty()
        )

        // Con su propia tolerancia calibrada (0° a ~17°, amplitud real del paciente) el
        // mismo movimiento sí supera el piso de prominencia derivado de SU rango.
        val withCalibration = comparator(
            repository = FakeRepository(reference(peak = 1.4f)),
            tolerance = PatientTolerance(
                tieneCalibracion = true,
                anguloMinimo = 0.0,
                anguloMaximo = Math.toDegrees(0.3)
            )
        ).compare("ex-1", cleanSignal(reps = 3, peak = 0.3f).asFlow()).toList()

        assertEquals(3, withCalibration.size)
        assertTrue(withCalibration.all { it.repDelta == 1 })
    }

    // ── Degradación ──────────────────────────────────────────────────────────

    @Test
    fun `sin referencia el flujo completa sin emitir`() = runBlocking {
        val results = comparator(FakeRepository(null))
            .compare("ex-1", cleanSignal(reps = 2).asFlow())
            .toList()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `referencia invalida degrada sin crashear`() = runBlocking {
        val throwing = object : ReferenceRepository {
            override suspend fun provenanceOf(exerciseId: String) = ReferenceProvenance.EMPAQUETADA

            override suspend fun getReference(exerciseId: String): ExerciseReference =
                throw IllegalArgumentException("pipelines desalineados")
        }
        val results = comparator(throwing)
            .compare("ex-1", cleanSignal(reps = 1).asFlow())
            .toList()

        assertTrue(results.isEmpty())
    }

    // ── Robustez ─────────────────────────────────────────────────────────────

    @Test
    fun `cuadros sin dimensiones validas se saltean sin romper la deteccion`() = runBlocking {
        val signal = cleanSignal(reps = 2).toMutableList()
        // Ráfaga de cuadros con confianza nula a mitad de la primera repetición.
        repeat(3) { i ->
            signal.add(8, poseWithElbowAngle(9f, 850L + i, confidence = 0f))
        }

        val results = comparator(FakeRepository(reference()))
            .compare("ex-1", signal.asFlow())
            .toList()

        assertEquals(2, results.size)
        assertEquals(MovementStatus.Good, results[1].status)
    }

    @Test
    fun `tolerancia marcada como calibrada pero sin angulos usa la amplitud de referencia`() = runBlocking {
        // Igual que el caso sin calibración: el movimiento de 0.3 rad no supera el piso
        // derivado de la referencia (1.4 rad) y no se cuentan reps.
        val results = comparator(
            repository = FakeRepository(reference(peak = 1.4f)),
            tolerance = PatientTolerance(tieneCalibracion = true, anguloMinimo = null, anguloMaximo = null)
        ).compare("ex-1", cleanSignal(reps = 3, peak = 0.3f).asFlow()).toList()

        assertTrue(results.isEmpty())
    }

    @Test
    fun `si el repositorio de tolerancia falla se degrada a la amplitud de referencia`() = runBlocking {
        val throwingTolerance = object : PatientToleranceRepository {
            override suspend fun getOwnTolerance(idEjercicio: String): Result<PatientTolerance> =
                throw IllegalStateException("sin red")
        }
        val comparator = DtwMovementComparator(
            referenceRepository = FakeRepository(reference()),
            angleExtractor = DefaultAngleExtractor(),
            segmenterFactory = RepetitionSegmenterFactory { index, amplitude ->
                PeakValleyRepetitionSegmenter.tunedFor(index, amplitude)
            },
            toleranceRepository = throwingTolerance,
            computeDispatcher = Dispatchers.Unconfined
        )

        val results = comparator.compare("ex-1", cleanSignal(reps = 3).asFlow()).toList()

        assertEquals(3, results.size)
        assertTrue(results.drop(1).all { it.status == MovementStatus.Good })
    }

    @Test
    fun `el log de salud advierte cuando la mayoria de los cuadros es invalida`() = runBlocking {
        val logs = mutableListOf<String>()
        val comparator = DtwMovementComparator(
            referenceRepository = FakeRepository(reference()),
            angleExtractor = DefaultAngleExtractor(),
            segmenterFactory = RepetitionSegmenterFactory { index, amplitude ->
                PeakValleyRepetitionSegmenter.tunedFor(index, amplitude)
            },
            toleranceRepository = FakeToleranceRepository(null),
            computeDispatcher = Dispatchers.Unconfined,
            log = { logs += it }
        )
        // 160 cuadros con confianza nula: ninguna rep, pero el reporte periódico debe
        // salir y señalar el encuadre como causa probable.
        val blind = List(160) { i -> poseWithElbowAngle(0f, i * 100L, confidence = 0f) }

        val results = comparator.compare("ex-1", blind.asFlow()).toList()

        assertTrue(results.isEmpty())
        val health = logs.filter { it.startsWith("Salud") }
        assertEquals(1, health.size)
        assertTrue(health.first(), health.first().contains("150 frames, 150 descartados"))
        assertTrue(health.first(), health.first().contains("articulaciones"))
    }

    @Test
    fun `el log de salud no advierte cuando los cuadros son mayormente validos`() = runBlocking {
        val logs = mutableListOf<String>()
        val comparator = DtwMovementComparator(
            referenceRepository = FakeRepository(reference()),
            angleExtractor = DefaultAngleExtractor(),
            segmenterFactory = RepetitionSegmenterFactory { index, amplitude ->
                PeakValleyRepetitionSegmenter.tunedFor(index, amplitude)
            },
            toleranceRepository = FakeToleranceRepository(null),
            computeDispatcher = Dispatchers.Unconfined,
            log = { logs += it }
        )
        // 8 reps × 20 cuadros > 150: fuerza al menos un reporte con cuadros válidos.
        comparator.compare("ex-1", cleanSignal(reps = 8).asFlow()).toList()

        val health = logs.filter { it.startsWith("Salud") }
        assertTrue(health.isNotEmpty())
        assertTrue(health.none { it.contains("articulaciones") })
    }
}
