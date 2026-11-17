package com.steadypath.app.data.comparison

import com.steadypath.app.domain.comparison.AngleExtractor
import com.steadypath.app.domain.comparison.AngleFrame
import com.steadypath.app.domain.comparison.ComparisonResult
import com.steadypath.app.domain.comparison.ErrorAttributor
import com.steadypath.app.domain.comparison.MovementComparator
import com.steadypath.app.domain.comparison.MovementStatus
import com.steadypath.app.domain.comparison.ReferenceAlignment
import com.steadypath.app.domain.comparison.RepMetricsSink
import com.steadypath.app.domain.comparison.RepetitionSegmenterFactory
import com.steadypath.app.domain.comparison.dtw.Dtw
import com.steadypath.app.domain.comparison.dtw.ShapeSimilarity
import com.steadypath.app.domain.comparison.dtw.WrappedAngularDistance
import com.steadypath.app.domain.model.PoseFrame
import com.steadypath.app.domain.repository.PatientToleranceRepository
import com.steadypath.app.domain.repository.ReferenceRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

/**
 * Comparación de movimiento por DTW: encadena extracción de ángulos → segmentación por
 * repetición → DTW contra la repetición de referencia, y emite un [ComparisonResult] por
 * repetición cerrada.
 *
 * **Backpressure**: todo el pipeline corre en [computeDispatcher] (fuera del main thread y
 * del hilo del analyzer de CameraX). La extracción y la segmentación son O(D) por cuadro y
 * se hacen en línea; el DTW por repetición corre en un worker aparte alimentado por un
 * canal con DROP_OLDEST — si una comparación tardara, se descarta la repetición más vieja
 * antes que frenar el stream de poses.
 *
 * **Degradación**: sin referencia para el ejercicio (o con una referencia inválida) el
 * flujo se completa sin emitir — equivalente al no-op, la UI queda en `Unknown`.
 *
 * Una banda DTW infactible da distancia +∞ → similitud 0 → `NeedsCorrection`: una
 * repetición cuya duración no se puede alinear ni con la banda es, en efecto, incorrecta.
 */
class DtwMovementComparator(
    private val referenceRepository: ReferenceRepository,
    private val angleExtractor: AngleExtractor,
    private val segmenterFactory: RepetitionSegmenterFactory,
    /** Tolerancia propia del paciente: si calibró el ejercicio, reemplaza la amplitud
     * genérica de la referencia al ajustar el segmentador de repeticiones. */
    private val toleranceRepository: PatientToleranceRepository,
    private val computeDispatcher: CoroutineDispatcher,
    /** Seam de logging para no atar esta clase a android.util.Log (tests JVM). */
    private val log: (String) -> Unit = {},
    private val errorAttributor: ErrorAttributor = ErrorAttributor(),
    /** Telemetría por rep para calibración offline; no-op en producción. */
    private val metricsSink: RepMetricsSink = RepMetricsSink.NO_OP
) : MovementComparator {

    override fun compare(exerciseId: String, poses: Flow<PoseFrame>): Flow<ComparisonResult> =
        channelFlow {
            val reference = runCatching { referenceRepository.getReference(exerciseId) }
                .getOrElse { e ->
                    log("Referencia inválida para '$exerciseId': ${e.message}")
                    null
                }
            if (reference == null) {
                log("Sin referencia para '$exerciseId' — comparación deshabilitada")
                return@channelFlow
            }

            val definition = reference.definition
            // Referencias autoradas offline pueden venir ancladas al reposo; el segmentador
            // ancla al valle del conductor — se rota la referencia a esa convención y los
            // umbrales de detección se derivan de la amplitud real del ejercicio.
            val profile = ReferenceAlignment.profileOf(reference.trajectory, definition.driverAngleIndex)
            // Rotada al valle Y normalizada a excursión: los offsets posturales no comparan.
            val referenceTrajectory = ReferenceAlignment.toStartDeviation(
                ReferenceAlignment.alignToDriverValley(reference.trajectory, profile.valleyIndex)
            )
            // Si el paciente calibró este ejercicio, su propio rango de movimiento reemplaza
            // la amplitud genérica de la referencia — cada paciente tiene una tolerancia
            // distinta y no todos llegan a la amplitud "ideal" de la referencia.
            val ownTolerance = runCatching { toleranceRepository.getOwnTolerance(exerciseId).getOrNull() }
                .getOrNull()
            val amplitudeRad = if (ownTolerance?.tieneCalibracion == true &&
                ownTolerance.anguloMinimo != null && ownTolerance.anguloMaximo != null
            ) {
                Math.toRadians(abs(ownTolerance.anguloMaximo - ownTolerance.anguloMinimo)).toFloat()
            } else {
                profile.amplitudeRad
            }
            log(
                "Referencia '$exerciseId' cargada: ${reference.trajectory.size} frames, " +
                    "ángulos=[${definition.angleDefinitions.joinToString { it.name }}], " +
                    "conductor=${definition.angleDefinitions[definition.driverAngleIndex].name}, " +
                    "amplitud=${profile.amplitudeRad} rad (referencia)" +
                    (if (amplitudeRad != profile.amplitudeRad) ", $amplitudeRad rad (calibración propia)" else "") +
                    ", rotada ${profile.valleyIndex} frames al valle"
            )
            val segmenter = segmenterFactory.create(definition.driverAngleIndex, amplitudeRad)
            val weights = FloatArray(definition.dimensions) { definition.angleDefinitions[it].weight }
            val dtw = Dtw(WrappedAngularDistance(weights))
            val scoring = reference.scoring

            // Las reps llegan como mínimo a ~800 ms (minPeakGapMs) y el DTW tarda µs-ms:
            // 8 pendientes son varios segundos de backlog antes de dropear la más vieja.
            val pendingReps = Channel<List<AngleFrame>>(
                capacity = 8,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )

            launch {
                var repIndex = 0
                for (repetition in pendingReps) {
                    repIndex++
                    // El primer segmento arranca en el reposo inicial, no en un valle: es un
                    // ciclo parcial. Cuenta y se muestra como correcta, pero sin score —
                    // puntuar medio ciclo contra el ciclo entero daría corrección falsa.
                    if (repIndex == 1) {
                        send(
                            ComparisonResult(
                                status = MovementStatus.Good,
                                repDelta = 1,
                                repIndex = repIndex
                            )
                        )
                        continue
                    }
                    // computePath: el camino alimenta la forma (NCC) y la atribución de errores.
                    val normalizedRep = ReferenceAlignment.toStartDeviation(repetition)
                    val result = dtw.distance(normalizedRep, referenceTrajectory, computePath = true)
                    val dtwSimilarity =
                        exp(-result.normalizedDistance / scoring.similarityScale).toFloat()
                    // Score final = similitud DTW × forma: el DTW mide desvío punto a punto
                    // ya alineado, la forma castiga los déficits de amplitud que el warping
                    // absorbe (pico 0.7 vs 1.4 alineaba "barato" y pasaba como Good).
                    val shape = result.path?.let {
                        ShapeSimilarity.score(normalizedRep, referenceTrajectory, it, weights)
                    } ?: 1f
                    val similarity = dtwSimilarity * shape
                    metricsSink.onRep(
                        exerciseId = exerciseId,
                        repIndex = repIndex,
                        normalizedDistance = result.normalizedDistance,
                        dtwSimilarity = dtwSimilarity,
                        shapeScore = shape,
                        finalScore = similarity
                    )
                    // TODO: calibrar con datos — banda binaria placeholder por ejercicio.
                    val status = if (similarity >= scoring.goodThreshold) {
                        MovementStatus.Good
                    } else {
                        MovementStatus.NeedsCorrection
                    }
                    val hints = if (status == MovementStatus.NeedsCorrection && result.path != null) {
                        errorAttributor.attribute(
                            userRep = normalizedRep,
                            reference = referenceTrajectory,
                            path = result.path,
                            definition = definition
                        )
                    } else {
                        emptyList()
                    }
                    send(
                        ComparisonResult(
                            status = status,
                            repDelta = 1,
                            repIndex = repIndex,
                            rawDistance = result.rawDistance,
                            normalizedDistance = result.normalizedDistance,
                            similarityScore = similarity,
                            shapeScore = shape,
                            hints = hints
                        )
                    )
                }
            }

            var framesTotal = 0
            var framesInvalid = 0
            var repsDetected = 0
            poses.collect { pose ->
                val angleFrame = angleExtractor.extract(pose, definition)
                framesTotal++
                // Cuadro sin ninguna dimensión utilizable: ni al buffer ni al detector.
                val invalid = !angleFrame.hasValidDimensions()
                if (invalid) framesInvalid++
                // Salud del stream cada ~15 s: sin esto, un encuadre que no muestra algún
                // keypoint de las ternas descarta todo en silencio y la UI queda muda.
                if (framesTotal % HEALTH_LOG_EVERY_FRAMES == 0) {
                    log(
                        "Salud '$exerciseId': $framesTotal frames, $framesInvalid descartados " +
                            "por keypoints no confiables, $repsDetected reps detectadas" +
                            if (framesInvalid > framesTotal / 2) {
                                " — ¿entran en cámara todas las articulaciones de las ternas?"
                            } else ""
                    )
                }
                if (invalid) return@collect
                val repetition = segmenter.onFrame(angleFrame)
                if (repetition != null) {
                    repsDetected++
                    log("Rep $repsDetected cerrada (${repetition.size} frames) → encolada para DTW")
                    pendingReps.trySend(repetition)
                }
            }
            pendingReps.close()
        }.flowOn(computeDispatcher)

    companion object {
        /** ~15 s a la cadencia real de inferencia (~10 fps). */
        private const val HEALTH_LOG_EVERY_FRAMES = 150
    }
}
