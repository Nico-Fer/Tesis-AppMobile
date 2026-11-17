package com.steadypath.app.domain.comparison

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Análisis del ángulo conductor de una trayectoria de referencia.
 *
 * [PeakValleyRepetitionSegmenter] ancla los segmentos en vivo en los MÍNIMOS del
 * conductor, pero una referencia autorada offline puede venir anclada al reposo — en los
 * ejercicios "peak-first" (sentadilla parcial, elevación de hombros con ángulo signado)
 * el reposo es el MÁXIMO del conductor y el ciclo queda desfasado medio período respecto
 * de los segmentos en vivo, arruinando el DTW. [alignToDriverValley] rota la referencia a
 * la convención del segmentador; para referencias ya ancladas al valle es un no-op.
 *
 * La amplitud des-envuelta alimenta [PeakValleyRepetitionSegmenter.tunedFor]: los
 * umbrales de detección se derivan de la excursión real del ejercicio, no de un fijo.
 */
object ReferenceAlignment {

    /** Amplitud pico-a-valle des-envuelta del conductor e índice de su mínimo. */
    class DriverProfile(val amplitudeRad: Float, val valleyIndex: Int)

    /** Conductor des-envuelto, paralelo a [trajectory]; NaN donde el conductor es inválido. */
    fun unwrappedDriver(trajectory: List<AngleFrame>, driverAngleIndex: Int): FloatArray {
        val out = FloatArray(trajectory.size) { Float.NaN }
        var initialized = false
        var unwrapped = 0f
        trajectory.forEachIndexed { index, frame ->
            if (driverAngleIndex >= frame.valid.size || !frame.valid[driverAngleIndex]) {
                return@forEachIndexed
            }
            val raw = frame.angles[driverAngleIndex]
            if (!initialized) {
                initialized = true
                unwrapped = raw
            } else {
                // Mismo unwrap que el segmentador: el delta se normaliza a (−π, π].
                unwrapped += atan2(
                    sin((raw - unwrapped).toDouble()),
                    cos((raw - unwrapped).toDouble())
                ).toFloat()
            }
            out[index] = unwrapped
        }
        return out
    }

    fun profileOf(trajectory: List<AngleFrame>, driverAngleIndex: Int): DriverProfile {
        val values = unwrappedDriver(trajectory, driverAngleIndex)
        var min = Float.NaN
        var max = Float.NaN
        var minIndex = 0
        values.forEachIndexed { index, v ->
            if (v.isNaN()) return@forEachIndexed
            if (min.isNaN() || v < min) {
                min = v
                minIndex = index
            }
            if (max.isNaN() || v > max) max = v
        }
        return DriverProfile(
            amplitudeRad = if (min.isNaN()) 0f else max - min,
            valleyIndex = minIndex
        )
    }

    /**
     * Rota la trayectoria (ciclo cerrado) para que arranque en el valle del conductor.
     * Los timestamps se re-estampan uniformes: son sintéticos y solo importa el orden.
     */
    fun alignToDriverValley(trajectory: List<AngleFrame>, valleyIndex: Int): List<AngleFrame> {
        if (valleyIndex <= 0 || trajectory.size < 2) return trajectory
        require(valleyIndex < trajectory.size) {
            "valleyIndex=$valleyIndex fuera de rango para ${trajectory.size} cuadros"
        }
        val start = trajectory.first().timestampMs
        val step = (trajectory.last().timestampMs - start) / (trajectory.size - 1)
        val rotated = trajectory.subList(valleyIndex, trajectory.size) +
            trajectory.subList(0, valleyIndex)
        return rotated.mapIndexed { i, frame ->
            AngleFrame(angles = frame.angles, valid = frame.valid, timestampMs = start + i * step)
        }
    }

    /**
     * Resta a cada dimensión su valor en el primer cuadro válido: la comparación mide la
     * EXCURSIÓN del ciclo, no el ángulo absoluto. La postura base (altura de silla,
     * ángulo de cámara) difiere entre usuario y referencia y no es un error de ejecución —
     * sin esto, un offset constante se disfraza de déficit de amplitud.
     */
    fun toStartDeviation(frames: List<AngleFrame>): List<AngleFrame> {
        if (frames.isEmpty()) return frames
        val d = frames.first().angles.size
        val baseline = FloatArray(d) { k ->
            frames.firstOrNull { k < it.valid.size && it.valid[k] }?.angles?.get(k) ?: Float.NaN
        }
        return frames.map { frame ->
            AngleFrame(
                angles = FloatArray(d) { k ->
                    val base = baseline[k]
                    if (base.isNaN()) frame.angles[k] else wrapDiff(frame.angles[k], base)
                },
                valid = frame.valid,
                timestampMs = frame.timestampMs
            )
        }
    }

    /** Diferencia angular a−b con envolvimiento, en (−π, π]. */
    private fun wrapDiff(a: Float, b: Float): Float = atan2(
        sin((a - b).toDouble()),
        cos((a - b).toDouble())
    ).toFloat()
}
