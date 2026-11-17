package com.steadypath.app.domain.comparison

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class PeakValleyRepetitionSegmenterTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun frame(driver: Float, ts: Long, valid: Boolean = true) = AngleFrame(
        angles = floatArrayOf(driver),
        valid = booleanArrayOf(valid),
        timestampMs = ts
    )

    /**
     * Señal triangular: [n] repeticiones 0 → [amplitude] → 0 con [framesUp]+[framesDown]
     * cuadros por rep a [stepMs] por cuadro, más una cola ascendente que permite confirmar
     * el valle de cierre de la última repetición.
     */
    private fun triangleSignal(
        n: Int,
        amplitude: Float = 1f,
        framesUp: Int = 10,
        framesDown: Int = 10,
        stepMs: Long = 100L,
        withTail: Boolean = true
    ): List<AngleFrame> {
        val frames = ArrayList<AngleFrame>()
        var ts = 0L
        frames.add(frame(0f, ts))
        repeat(n) {
            for (i in 1..framesUp) {
                ts += stepMs
                frames.add(frame(amplitude * i / framesUp, ts))
            }
            for (i in 1..framesDown) {
                ts += stepMs
                frames.add(frame(amplitude * (framesDown - i) / framesDown, ts))
            }
        }
        if (withTail) {
            for (i in 1..5) {
                ts += stepMs
                frames.add(frame(amplitude * i / 10f, ts))
            }
        }
        return frames
    }

    private fun collectReps(
        segmenter: RepetitionSegmenter,
        signal: List<AngleFrame>
    ): List<List<AngleFrame>> = signal.mapNotNull { segmenter.onFrame(it) }

    private fun segmenter() = PeakValleyRepetitionSegmenter(driverAngleIndex = 0)

    // ── Detección básica ─────────────────────────────────────────────────────

    @Test
    fun `detecta N repeticiones limpias`() {
        val reps = collectReps(segmenter(), triangleSignal(n = 5))
        assertEquals(5, reps.size)
    }

    @Test
    fun `una sola repeticion con cola`() {
        val reps = collectReps(segmenter(), triangleSignal(n = 1))
        assertEquals(1, reps.size)
    }

    @Test
    fun `la ultima repeticion no cierra sin retroceso posterior`() {
        // Sin cola: el valle final nunca se confirma (histéresis) → n−1 cierres.
        val reps = collectReps(segmenter(), triangleSignal(n = 3, withTail = false))
        assertEquals(2, reps.size)
    }

    @Test
    fun `cada repeticion cubre el ciclo completo`() {
        val reps = collectReps(segmenter(), triangleSignal(n = 3))

        for (rep in reps) {
            val driver = rep.filter { it.valid[0] }.map { it.angles[0] }
            assertTrue("La rep debe alcanzar el pico", driver.max() > 0.9f)
            assertTrue("La rep debe arrancar y cerrar en valle", driver.first() < 0.2f && driver.last() < 0.2f)
        }
    }

    @Test
    fun `el valle de cierre se comparte con la repeticion siguiente`() {
        val reps = collectReps(segmenter(), triangleSignal(n = 2))

        assertEquals(2, reps.size)
        val boundary = reps[0].last()
        assertEquals(boundary.timestampMs, reps[1].first().timestampMs)
    }

    // ── Robustez ─────────────────────────────────────────────────────────────

    @Test
    fun `ruido menor a la histeresis no duplica picos`() {
        // Meseta ruidosa en el pico: oscilaciones de ±0.05 rad (< histéresis 0.15).
        val frames = ArrayList<AngleFrame>()
        var ts = 0L
        frames.add(frame(0f, ts))
        for (i in 1..10) { ts += 100; frames.add(frame(i / 10f, ts)) }
        for (i in 0 until 8) { ts += 100; frames.add(frame(if (i % 2 == 0) 1f else 0.95f, ts)) }
        for (i in 1..10) { ts += 100; frames.add(frame((10 - i) / 10f, ts)) }
        for (i in 1..5) { ts += 100; frames.add(frame(i / 10f, ts)) }

        val reps = collectReps(segmenter(), frames)
        assertEquals(1, reps.size)
    }

    @Test
    fun `oscilaciones sin prominencia suficiente no cuentan`() {
        // Amplitud 0.2 rad < prominencia mínima 0.35.
        val reps = collectReps(segmenter(), triangleSignal(n = 5, amplitude = 0.2f))
        assertEquals(0, reps.size)
    }

    @Test
    fun `picos demasiado cercanos en el tiempo no se cuentan dos veces`() {
        // Ciclos completos cada 200 ms (< minPeakGapMs=800): solo el primero cierra.
        val reps = collectReps(segmenter(), triangleSignal(n = 10, stepMs = 10L))
        assertEquals(1, reps.size)
    }

    // ── Ejercicios peak-first (reposo en el máximo del conductor) ────────────

    /**
     * Señal peak-first: arranca en reposo [rest] y baja [amplitude] por rep
     * (sentadilla, elevación de hombros con ángulo signado). Sin cola: el cierre
     * de cada rep lo confirma la propia subida de regreso al reposo.
     */
    private fun peakFirstSignal(
        n: Int,
        rest: Float = 1.4f,
        amplitude: Float = 1.4f,
        stepMs: Long = 100L
    ): List<AngleFrame> {
        val frames = ArrayList<AngleFrame>()
        var ts = 0L
        frames.add(frame(rest, ts))
        repeat(n) {
            for (i in 1..10) { ts += stepMs; frames.add(frame(rest - amplitude * i / 10f, ts)) }
            for (i in 1..10) { ts += stepMs; frames.add(frame(rest - amplitude * (10 - i) / 10f, ts)) }
        }
        return frames
    }

    @Test
    fun `senal peak-first cuenta todas las repeticiones incluida la ultima`() {
        // La vuelta al reposo confirma cada valle: N dips → N cierres, sin cola.
        val reps = collectReps(segmenter(), peakFirstSignal(n = 4))
        assertEquals(4, reps.size)
    }

    // ── Umbrales derivados de la referencia (tunedFor) ───────────────────────

    @Test
    fun `sentadilla parcial mas superficial que la referencia cuenta con tunedFor`() {
        // Amplitud referencia ~0.4 rad (sentadilla parcial real); paciente a 0.3 rad.
        // Default fijo (0.35): 0 reps. Umbral derivado (max(0.25×0.4, 0.15)=0.15): todas.
        val signal = peakFirstSignal(n = 3, rest = 2.85f, amplitude = 0.3f)

        assertEquals(0, collectReps(segmenter(), signal).size)
        val tuned = PeakValleyRepetitionSegmenter.tunedFor(driverAngleIndex = 0, driverAmplitudeRad = 0.4f)
        assertEquals(3, collectReps(tuned, signal).size)
    }

    @Test
    fun `tunedFor exige mas excursion cuando la referencia es amplia`() {
        // Referencia amplia (1.4 rad): prominencia 0.35 — oscilar 0.3 rad no es una rep.
        val tuned = PeakValleyRepetitionSegmenter.tunedFor(driverAngleIndex = 0, driverAmplitudeRad = 1.4f)
        val reps = collectReps(tuned, triangleSignal(n = 5, amplitude = 0.3f))
        assertEquals(0, reps.size)
    }

    @Test
    fun `excursion chica tipo elevacion de talones cuenta gracias al cap del piso`() {
        // Amplitud referencia ~0.17 rad: el piso fijo (0.15) exigiría el 90% de la
        // amplitud; el cap 0.5×amp lo baja a ~0.085 y las reps reales cuentan.
        val signal = peakFirstSignal(n = 3, rest = -2.6f, amplitude = 0.15f)

        assertEquals(0, collectReps(segmenter(), signal).size)
        val tuned = PeakValleyRepetitionSegmenter.tunedFor(driverAngleIndex = 0, driverAmplitudeRad = 0.17f)
        assertEquals(3, collectReps(tuned, signal).size)
    }

    @Test
    fun `ruido puntual en ejercicios chicos no duplica reps gracias al suavizado`() {
        // Espiga de +0.08 rad en plena bajada: sin EMA supera la histéresis (0.05), cierra
        // un valle falso y parte cada rep en dos; el suavizado de tunedFor la deja en ~0.04.
        val rest = -2.6f
        val dip = 0.15f
        val frames = ArrayList<AngleFrame>()
        var ts = 0L
        frames.add(frame(rest, ts))
        repeat(2) {
            for (i in 1..10) {
                ts += 110
                val spike = if (i == 8) 0.08f else 0f
                frames.add(frame(rest - dip * i / 10f + spike, ts))
            }
            for (i in 1..10) {
                ts += 110
                frames.add(frame(rest - dip * (10 - i) / 10f, ts))
            }
        }

        val sinSuavizado = PeakValleyRepetitionSegmenter(
            driverAngleIndex = 0,
            minProminenceRad = 0.085f,
            hysteresisRad = 0.05f
        )
        val tuned = PeakValleyRepetitionSegmenter.tunedFor(driverAngleIndex = 0, driverAmplitudeRad = 0.17f)

        assertTrue(
            "Sin suavizado la espiga debería duplicar reps",
            collectReps(sinSuavizado, frames).size > 2
        )
        assertEquals(2, collectReps(tuned, frames).size)
    }

    @Test
    fun `tunedFor sin amplitud cae en los defaults`() {
        val tuned = PeakValleyRepetitionSegmenter.tunedFor(driverAngleIndex = 0, driverAmplitudeRad = null)
        assertEquals(5, collectReps(tuned, triangleSignal(n = 5)).size)
        assertEquals(0, collectReps(
            PeakValleyRepetitionSegmenter.tunedFor(driverAngleIndex = 0, driverAmplitudeRad = null),
            triangleSignal(n = 5, amplitude = 0.2f)
        ).size)
    }

    @Test
    fun `cuadros con conductor invalido se acumulan sin romper la deteccion`() {
        val signal = triangleSignal(n = 2).toMutableList()
        // Inyecta cuadros inválidos a mitad de la primera subida.
        signal.add(5, frame(99f, 450L, valid = false))
        signal.add(6, frame(-99f, 460L, valid = false))

        val reps = collectReps(segmenter(), signal)

        assertEquals(2, reps.size)
        assertTrue("Los cuadros inválidos pertenecen a la repetición",
            reps[0].any { !it.valid[0] })
    }

    // ── Envolvimiento ────────────────────────────────────────────────────────

    @Test
    fun `ejercicio que cruza mas menos pi se segmenta igual`() {
        // Sube de π−0.5 a π+0.5 (que envuelto es −π+0.5) y vuelve, dos veces.
        val pi = PI.toFloat()
        fun wrap(v: Float): Float = if (v > pi) v - 2f * pi else v
        val frames = ArrayList<AngleFrame>()
        var ts = 0L
        frames.add(frame(pi - 0.5f, ts))
        repeat(2) {
            for (i in 1..10) { ts += 100; frames.add(frame(wrap(pi - 0.5f + i / 10f), ts)) }
            for (i in 1..10) { ts += 100; frames.add(frame(wrap(pi + 0.5f - i / 10f), ts)) }
        }
        for (i in 1..5) { ts += 100; frames.add(frame(wrap(pi - 0.5f + i / 10f), ts)) }

        val reps = collectReps(segmenter(), frames)
        assertEquals(2, reps.size)
    }

    // ── Estado ───────────────────────────────────────────────────────────────

    @Test
    fun `reset descarta la repeticion en curso`() {
        val seg = segmenter()
        // Media repetición…
        triangleSignal(n = 1, withTail = false).take(12).forEach { seg.onFrame(it) }
        seg.reset()
        // …y una sesión limpia después del reset.
        val reps = collectReps(seg, triangleSignal(n = 3))

        assertEquals(3, reps.size)
    }

    @Test
    fun `ventana sin ciclo detectable se descarta y no crece sin limite`() {
        val seg = PeakValleyRepetitionSegmenter(driverAngleIndex = 0, maxRepFrames = 50)
        // 200 cuadros planos: nunca hay repetición.
        var ts = 0L
        repeat(200) {
            ts += 100
            val closed = seg.onFrame(frame(0f, ts))
            assertEquals(null, closed)
        }
        // Después del descarte sigue detectando normalmente.
        val reps = collectReps(seg, triangleSignal(n = 2).map { frame(it.angles[0], ts + it.timestampMs) })
        assertEquals(2, reps.size)
    }
}
