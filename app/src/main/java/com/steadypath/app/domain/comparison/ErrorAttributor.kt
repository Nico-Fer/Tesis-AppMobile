package com.steadypath.app.domain.comparison

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Descompone el error de una repetición por DIMENSIÓN (qué ángulo) y por FASE del ciclo
 * (subida / pico / bajada), usando el camino DTW como mapa de correspondencia temporal:
 * cada par (i, j) del camino compara el frame i del usuario contra el frame j de la
 * referencia, así que el error se mide entre instantes equivalentes del ciclo, no entre
 * índices crudos.
 *
 * Kotlin puro, sin dependencias de Android — testeable en JVM.
 *
 * Convenciones:
 * - Las fases se definen sobre la REFERENCIA (que cubre exactamente un ciclo
 *   valle→pico→valle del ángulo conductor): el pico es el extremo de la trayectoria del
 *   conductor y una ventana alrededor es [RepPhase.PICO].
 * - Amplitud: el error medio firmado (usuario − referencia, con envolvimiento) se alinea
 *   con la dirección de movimiento de cada dimensión en la referencia, de modo que
 *   [ErrorDirection.UNDERSHOOT] siempre significa "no llegó tan lejos como la referencia"
 *   sin importar el signo del ángulo.
 * - Tempo: la pendiente del camino DTW por fase (frames de usuario por frame de
 *   referencia) detecta ejecución lenta/rápida; se atribuye al ángulo conductor.
 */
class ErrorAttributor(
    private val amplitudeThresholdRad: Float = DEFAULT_AMPLITUDE_THRESHOLD_RAD,
    private val slowTempoRatioThreshold: Float = DEFAULT_SLOW_TEMPO_RATIO_THRESHOLD,
    private val fastTempoRatioThreshold: Float = DEFAULT_FAST_TEMPO_RATIO_THRESHOLD,
    private val peakWindowFraction: Float = DEFAULT_PEAK_WINDOW_FRACTION,
    private val maxHints: Int = DEFAULT_MAX_HINTS
) {

    fun attribute(
        userRep: List<AngleFrame>,
        reference: List<AngleFrame>,
        path: List<Pair<Int, Int>>,
        definition: ExerciseDefinition
    ): List<CorrectionHint> {
        if (userRep.isEmpty() || reference.isEmpty() || path.isEmpty()) return emptyList()

        val d = definition.dimensions
        val driver = definition.driverAngleIndex
        val phases = referencePhases(reference, driver)
        val hints = mutableListOf<CorrectionHint>()

        // ── Amplitud: error medio firmado por dimensión × fase ──────────────
        // Dirección de movimiento de cada dimensión en la referencia (inicio → pico).
        val peakIdx = peakIndex(reference, driver)
        val motionSign = FloatArray(d) { k ->
            val delta = wrapDiff(reference[peakIdx].angles[k], reference[0].angles[k])
            if (delta >= 0f) 1f else -1f
        }

        for (phase in RepPhase.entries) {
            val pairsInPhase = path.filter { (_, j) -> phases[j] == phase }
            if (pairsInPhase.isEmpty()) continue

            var amplitudeHintInPhase = false
            for (k in 0 until d) {
                var sum = 0.0
                var count = 0
                for ((i, j) in pairsInPhase) {
                    if (!userRep[i].valid[k] || !reference[j].valid[k]) continue
                    sum += wrapDiff(userRep[i].angles[k], reference[j].angles[k]).toDouble()
                    count++
                }
                if (count == 0) continue
                // Alineado con la dirección de movimiento: negativo = se quedó corto.
                val alignedError = (sum / count).toFloat() * motionSign[k]
                if (abs(alignedError) <= amplitudeThresholdRad) continue
                amplitudeHintInPhase = true
                hints += CorrectionHint(
                    angleIndex = k,
                    angleName = definition.angleDefinitions[k].name,
                    direction = if (alignedError < 0) ErrorDirection.UNDERSHOOT else ErrorDirection.OVERSHOOT,
                    phase = phase,
                    severity = abs(alignedError) / amplitudeThresholdRad,
                    isDriver = k == driver
                )
            }

            // ── Tempo: pendiente del camino en esta fase ─────────────────────
            // Solo si la amplitud de la fase está limpia: un déficit de amplitud deforma
            // el camino (muchos frames de referencia comprimidos sobre el pico corto del
            // usuario) y se disfrazaría de error de tempo espurio.
            // Tempo por DURACIÓN real (timestamps), no cantidad de cuadros: la referencia
            // (30 fps) y la inferencia en vivo (~10-15 fps) tienen cadencias distintas y
            // comparar frames marcaba TOO_FAST espurio a velocidad idéntica.
            if (amplitudeHintInPhase) continue
            val userIdx = pairsInPhase.map { it.first }.distinct()
            val refIdx = pairsInPhase.map { it.second }.distinct()
            if (refIdx.size >= MIN_PHASE_FRAMES && userIdx.size >= 2) {
                val refMs = (reference[refIdx.max()].timestampMs - reference[refIdx.min()].timestampMs).toFloat()
                val userMs = (userRep[userIdx.max()].timestampMs - userRep[userIdx.min()].timestampMs).toFloat()
                if (refMs > 0f && userMs > 0f) {
                    val ratio = userMs / refMs
                    // Asimétrico a propósito: el público objetivo (adultos mayores) ejecuta
                    // más lento que la referencia — sostener el extremo no es un error.
                    val direction = when {
                        ratio > slowTempoRatioThreshold -> ErrorDirection.TOO_SLOW
                        ratio < 1f / fastTempoRatioThreshold -> ErrorDirection.TOO_FAST
                        else -> null
                    }
                    if (direction != null) {
                        hints += CorrectionHint(
                            angleIndex = driver,
                            angleName = definition.angleDefinitions[driver].name,
                            direction = direction,
                            phase = phase,
                            severity = if (direction == ErrorDirection.TOO_SLOW) {
                                ratio / slowTempoRatioThreshold
                            } else {
                                (1f / ratio) / fastTempoRatioThreshold
                            },
                            isDriver = true
                        )
                    }
                }
            }
        }

        return hints.sortedByDescending { it.severity }.take(maxHints)
    }

    /** Índice del extremo de la trayectoria del conductor (máximo desvío desde el inicio). */
    private fun peakIndex(reference: List<AngleFrame>, driver: Int): Int {
        val start = reference[0].angles[driver]
        var best = 0
        var bestDev = 0f
        reference.forEachIndexed { j, frame ->
            val dev = abs(wrapDiff(frame.angles[driver], start))
            if (dev > bestDev) {
                bestDev = dev
                best = j
            }
        }
        return best
    }

    /** Fase de cada índice de la referencia según su posición relativa al pico. */
    private fun referencePhases(reference: List<AngleFrame>, driver: Int): Array<RepPhase> {
        val peak = peakIndex(reference, driver)
        val window = max(1, (reference.size * peakWindowFraction).roundToInt())
        return Array(reference.size) { j ->
            when {
                abs(j - peak) <= window -> RepPhase.PICO
                j < peak -> RepPhase.SUBIDA
                else -> RepPhase.BAJADA
            }
        }
    }

    /** Diferencia angular a−b con envolvimiento, en (−π, π]. */
    private fun wrapDiff(a: Float, b: Float): Float {
        val raw = (a - b).toDouble()
        return atan2(sin(raw), cos(raw)).toFloat()
    }

    companion object {
        /** TODO: calibrar con datos — todos placeholders. */
        const val DEFAULT_AMPLITUDE_THRESHOLD_RAD = 0.35f

        /** Asimétricos: lento se tolera más que rápido (público = adultos mayores). */
        const val DEFAULT_SLOW_TEMPO_RATIO_THRESHOLD = 3.0f
        const val DEFAULT_FAST_TEMPO_RATIO_THRESHOLD = 1.9f

        const val DEFAULT_PEAK_WINDOW_FRACTION = 0.15f
        const val DEFAULT_MAX_HINTS = 2

        /** Fases con menos frames de referencia que esto no dan evidencia de tempo. */
        private const val MIN_PHASE_FRAMES = 3
    }
}
