package com.steadypath.app.domain.comparison

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [AdaptiveRestRepetitionSegmenter] contra el ángulo conductor de videos reales, extraído
 * con modelosCV (`scripts/probe_reference_angles.py --cache`, mismo `.tflite` INT8 de la
 * pipeline de referencias) y exportado a `src/test/resources/recording/`. Cada CSV dice en
 * su cabecera de qué video sale y cuántas reps se ven.
 *
 * Los videos no arrancan quietos como pide la app («Quedate quieto…»), así que al exportar
 * se antepuso el tramo más quieto del propio clip. Se reproduce a 30, 15 y 10 fps porque en
 * el teléfono la estimación de pose corre a 10–20 fps.
 *
 * El mínimo es lo que el segmentador logra en el peor fps; el segmentador anterior,
 * anclado al reposo, contaba sobre los mismos datos 1 de 2 en brazos, 5 de 7 en talones y
 * 0–2 en extensión de rodilla. El máximo es lo que se ve en el video: sin amplitud mínima,
 * los acomodos de la extensión "con manos" (18 s agarrándose la pierna antes de la serie)
 * se cuentan hasta que llega la primera rep real y los descarta; si alguno quedara, la
 * cuenta superaría las reps visibles y este test fallaría.
 */
class RecordingSegmenterRealDataTest {

    private data class Clip(val file: String, val minReps: Int, val visibleReps: Int)

    private val clips = listOf(
        Clip("sentadilla.csv", minReps = 5, visibleReps = 5),
        Clip("elevacion_brazos.csv", minReps = 2, visibleReps = 2),
        Clip("talones.csv", minReps = 6, visibleReps = 7),
        Clip("extension_rodilla_sin_manos.csv", minReps = 3, visibleReps = 5),
        Clip("extension_rodilla_con_manos.csv", minReps = 3, visibleReps = 5)
    )

    /** Cuenta neta, como el ViewModel: los descartes restan. */
    private class Counter : RecordingSegmenterListener {
        var count = 0
        override fun onCalibrated() = Unit
        override fun onRejected(reason: RepRejectReason) = Unit
        override fun onRetracted(count: Int) { this.count -= count }
    }

    private fun load(file: String): List<AngleFrame> {
        val stream = checkNotNull(javaClass.classLoader?.getResourceAsStream("recording/$file")) {
            "Falta el recurso recording/$file"
        }
        return stream.bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("ts_ms") }
                .map { line ->
                    val (ts, angle, valid) = line.split(',')
                    AngleFrame(
                        angles = floatArrayOf(angle.toFloat()),
                        valid = booleanArrayOf(valid == "1"),
                        timestampMs = ts.toLong()
                    )
                }
                .toList()
        }
    }

    private fun count(frames: List<AngleFrame>, every: Int): Int {
        val counter = Counter()
        val segmenter = AdaptiveRestRepetitionSegmenter(driverAngleIndex = 0, listener = counter)
        frames.filterIndexed { i, _ -> i % every == 0 }.forEach { if (segmenter.onFrame(it) != null) counter.count++ }
        return counter.count
    }

    @Test
    fun `cuenta las reps de videos reales sin inventar de mas`() {
        val failures = ArrayList<String>()
        val summary = StringBuilder()
        for (clip in clips) {
            val frames = load(clip.file)
            for ((every, fps) in listOf(1 to 30, 2 to 15, 3 to 10)) {
                val reps = count(frames, every)
                summary.append("${clip.file}@${fps}fps=$reps ")
                if (reps < clip.minReps || reps > clip.visibleReps) {
                    failures.add("${clip.file} a $fps fps: $reps (esperado ${clip.minReps}..${clip.visibleReps})")
                }
            }
        }
        assertTrue("$failures\n$summary", failures.isEmpty())
    }
}
