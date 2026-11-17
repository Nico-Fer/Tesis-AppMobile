package com.steadypath.app.domain.comparison

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

class AdaptiveRestRepetitionSegmenterTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Lleva la cuenta como el ViewModel: un descarte saca las últimas reps devueltas. */
    private class RecordingListener : RecordingSegmenterListener {
        var calibrated = 0
        var retracted = 0
        val rejections = ArrayList<RepRejectReason>()
        val reps = ArrayList<List<AngleFrame>>()
        override fun onCalibrated() { calibrated++ }
        override fun onRejected(reason: RepRejectReason) { rejections.add(reason) }
        override fun onRetracted(count: Int) {
            retracted += count
            repeat(count) { reps.removeAt(reps.lastIndex) }
        }
    }

    private fun frame(driver: Float, ts: Long) = AngleFrame(
        angles = floatArrayOf(driver),
        valid = booleanArrayOf(true),
        timestampMs = ts
    )

    private val restMs = 2000L

    /**
     * Constructor de señal. [level] es donde está el conductor; [move] lo lleva a otro lado
     * con una transición suave (vuelve a un reposo distinto, como pasa con brazos y piernas).
     */
    private inner class Signal(
        base: Float = 2.0f,
        private val noiseSigma: Float = 0f,
        seed: Long = 42,
        private val step: Long = 50L // 20 fps
    ) {
        private val rng = Random(seed)
        val frames = ArrayList<AngleFrame>()
        private var ts = 0L
        var level = base
            private set

        private fun noise() = if (noiseSigma == 0f) 0f else (rng.nextGaussian() * noiseSigma).toFloat()

        private fun emit(value: Float) {
            frames.add(frame(value + noise(), ts))
            ts += step
        }

        private fun count(ms: Long) = (ms / step).toInt()

        fun rest(ms: Long) = apply { repeat(count(ms)) { emit(level) } }

        fun move(to: Float, ms: Long) = apply {
            val from = level
            val n = count(ms)
            for (i in 1..n) emit(from + (to - from) * (0.5f * (1f - cos(PI * i / n).toFloat())))
            level = to
        }

        fun pulse(amplitude: Float, durationMs: Long) = apply {
            val n = count(durationMs)
            for (i in 1..n) emit(level + amplitude * 0.5f * (1f - cos(2.0 * PI * i / n)).toFloat())
        }

        /** Sube, sostiene temblando [wobble]·amplitud y vuelve a [returnTo] (no al reposo exacto). */
        fun rep(amplitude: Float, returnTo: Float, upMs: Long = 1200L, holdMs: Long = 500L, downMs: Long = 1200L, wobble: Float = 0f) =
            apply {
                val top = level + amplitude
                move(top, upMs)
                val n = count(holdMs)
                for (i in 1..n) emit(top - amplitude * wobble * 0.5f * (1f - cos(2.0 * PI * i / n).toFloat()))
                move(returnTo, downMs)
            }

        fun ramp(total: Float, durationMs: Long) = apply {
            val start = level
            val n = count(durationMs)
            for (i in 1..n) emit(start + total * i / n)
        }

        fun humps(amplitude: Float, dipFraction: Float, count: Int, durationMs: Long) = apply {
            val n = count(durationMs)
            for (i in 1..n) {
                val t = i.toDouble() / n
                val envelope = 0.5 * (1 - cos(2 * PI * t))
                val ripple = 0.5 * (1 - cos(2 * PI * count * t))
                val dev = amplitude * (dipFraction + (1 - dipFraction) * ripple) * (if (t < 0.1 || t > 0.9) envelope else 1.0)
                emit(level + dev.toFloat())
            }
        }

        fun reps(n: Int, amplitude: Float, repMs: Long = 2000L, gapMs: Long = 1000L) = apply {
            repeat(n) {
                pulse(amplitude, repMs)
                rest(gapMs)
            }
        }

        fun spike(delta: Float) = apply { emit(level + delta) }
    }

    private fun run(
        signal: Signal,
        listener: RecordingListener = RecordingListener()
    ): Pair<List<List<AngleFrame>>, RecordingListener> {
        val segmenter = AdaptiveRestRepetitionSegmenter(driverAngleIndex = 0, listener = listener)
        signal.frames.forEach { frame -> segmenter.onFrame(frame)?.let { listener.reps.add(it) } }
        return listener.reps to listener
    }

    // ── Lo mismo que el segmentador original ─────────────────────────────────

    @Test
    fun `cuenta reps superficiales con ruido realista desde la primera`() {
        val (reps, listener) = run(Signal(noiseSigma = 0.015f).rest(restMs).reps(5, amplitude = 0.15f))
        assertEquals(1, listener.calibrated)
        assertEquals(5, reps.size)
        assertTrue("No debería rechazar nada: ${listener.rejections}", listener.rejections.isEmpty())
    }

    @Test
    fun `ruido puro no cuenta reps`() {
        val (reps, _) = run(Signal(noiseSigma = 0.02f).rest(20_000L))
        assertEquals(0, reps.size)
    }

    @Test
    fun `reposo en el maximo del angulo entrega reps completas`() {
        val (reps, _) = run(Signal(noiseSigma = 0.01f).rest(restMs).reps(4, amplitude = -0.3f))
        assertEquals(4, reps.size)
        for (rep in reps) {
            val first = rep.first().angles[0]
            val last = rep.last().angles[0]
            assertTrue("Arranca en reposo ($first)", abs(first - 2.0f) < 0.08f)
            assertTrue("Cierra en reposo ($last)", abs(last - 2.0f) < 0.08f)
            assertTrue("Llega al extremo", rep.minOf { it.angles[0] } < 2.0f - 0.25f)
        }
    }

    @Test
    fun `nada se emite durante la calibracion aunque haya movimiento`() {
        val listener = RecordingListener()
        val segmenter = AdaptiveRestRepetitionSegmenter(driverAngleIndex = 0, listener = listener)
        Signal().pulse(0.5f, 1000L).frames.forEach { assertEquals(null, segmenter.onFrame(it)) }
        assertEquals(0, listener.calibrated)
    }

    @Test
    fun `una deriva lenta no cuenta como rep`() {
        val (reps, _) = run(Signal(noiseSigma = 0.01f).rest(restMs).ramp(0.3f, 30_000L).rest(3000L))
        assertEquals(0, reps.size)
    }

    @Test
    fun `una excursion demasiado rapida se rechaza por TOO_SHORT`() {
        val (reps, listener) = run(Signal().rest(restMs).pulse(0.4f, 300L).rest(1000L))
        assertEquals(0, reps.size)
        assertEquals(listOf(RepRejectReason.TOO_SHORT), listener.rejections)
    }

    @Test
    fun `una excursion minima pero sostenida cuenta`() {
        // Apenas sale del reposo (umbral 0.08): no hay amplitud mínima, la referencia es
        // lo que la persona puede hacer.
        val (reps, listener) = run(Signal().rest(restMs).pulse(0.095f, 2000L).rest(1000L))
        assertEquals(1, reps.size)
        assertTrue(listener.rejections.isEmpty())
    }

    @Test
    fun `una rep en direccion opuesta se rechaza y no corta la cuenta`() {
        val signal = Signal(noiseSigma = 0.01f).rest(restMs)
            .reps(2, amplitude = 0.3f)
            .reps(1, amplitude = -0.3f)
            .reps(1, amplitude = 0.3f)
        val (reps, listener) = run(signal)
        assertEquals(3, reps.size)
        assertEquals(listOf(RepRejectReason.WRONG_DIRECTION), listener.rejections)
    }

    @Test
    fun `varias jorobas sin volver al reposo se rechazan por BAD_SHAPE`() {
        val (reps, listener) = run(
            Signal().rest(restMs).humps(0.3f, dipFraction = 0.4f, count = 3, durationMs = 4000L).rest(1000L)
        )
        assertEquals(0, reps.size)
        assertEquals(listOf(RepRejectReason.BAD_SHAPE), listener.rejections)
    }

    @Test
    fun `un pico de ruido breve se descarta en silencio`() {
        val (reps, listener) = run(Signal().rest(restMs).spike(0.1f).rest(1000L))
        assertEquals(0, reps.size)
        assertTrue(listener.rejections.isEmpty())
    }

    @Test
    fun `reset vuelve a calibrar`() {
        val listener = RecordingListener()
        val segmenter = AdaptiveRestRepetitionSegmenter(driverAngleIndex = 0, listener = listener)
        Signal().rest(restMs).reps(1, 0.3f).frames.forEach { segmenter.onFrame(it) }
        assertEquals(1, listener.calibrated)
        segmenter.reset()
        assertEquals(0f, segmenter.exitThresholdRad)
        Signal().rest(restMs).frames.forEach { segmenter.onFrame(it) }
        assertEquals(2, listener.calibrated)
    }

    @Test
    fun `el umbral de salida escala con el ruido medido pero respeta el piso`() {
        val quiet = AdaptiveRestRepetitionSegmenter(0, RecordingListener())
        Signal(noiseSigma = 0.005f).rest(restMs).frames.forEach { quiet.onFrame(it) }
        assertEquals(AdaptiveRestRepetitionSegmenter.MIN_EXIT_RAD, quiet.exitThresholdRad, 1e-6f)

        // Ruido alto: puede no pasar el umbral de quietud y calibrar recién en el timeout.
        val noisy = AdaptiveRestRepetitionSegmenter(0, RecordingListener())
        Signal(noiseSigma = 0.05f).rest(AdaptiveRestRepetitionSegmenter.CALIBRATION_TIMEOUT_MS + 1000L)
            .frames.forEach { noisy.onFrame(it) }
        assertTrue(noisy.exitThresholdRad > AdaptiveRestRepetitionSegmenter.MIN_EXIT_RAD)
        assertTrue(noisy.exitThresholdRad <= AdaptiveRestRepetitionSegmenter.MAX_EXIT_RAD)
    }

    // ── Lo que el original no contaba ────────────────────────────────────────
    // Brazos y piernas no vuelven al reposo exacto: a 15 fps, como en un teléfono.

    private val armStep = 66L
    private val armRest = 0.2f
    private val armAmplitude = -1.4f

    @Test
    fun `una sentadilla que apenas baja cuenta las cinco`() {
        // Cadera parada ≈ 2.95 rad (reposo en el máximo); baja ~6° y vuelve.
        val signal = Signal(base = 2.95f, noiseSigma = 0.015f, step = armStep).rest(2500L)
        repeat(5) { signal.rep(-0.1f, returnTo = 2.95f, upMs = 800L, holdMs = 300L, downMs = 800L).rest(1000L) }
        val (reps, listener) = run(signal)
        assertEquals("Rechazos: ${listener.rejections}", 5, reps.size)
    }

    @Test
    fun `brazos que apenas suben cuentan las cinco`() {
        val signal = Signal(base = armRest, noiseSigma = 0.015f, step = armStep).rest(2500L)
        repeat(5) { signal.rep(-0.15f, returnTo = armRest - 0.02f, upMs = 900L, holdMs = 300L, downMs = 900L).rest(1000L) }
        val (reps, listener) = run(signal)
        assertEquals("Rechazos: ${listener.rejections}", 5, reps.size)
    }

    @Test
    fun `brazos que vuelven unos grados antes del reposo cuentan las cinco`() {
        val signal = Signal(base = armRest, noiseSigma = 0.015f, step = armStep).rest(2500L)
        repeat(5) { signal.rep(armAmplitude, returnTo = armRest - 0.1f).rest(1000L) }
        val (reps, listener) = run(signal)
        assertEquals("Rechazos: ${listener.rejections}", 5, reps.size)
    }

    @Test
    fun `brazos encadenados sin pausa abajo cierran en el valle`() {
        val signal = Signal(base = armRest, noiseSigma = 0.015f, step = armStep).rest(2500L)
        repeat(5) { signal.rep(armAmplitude, returnTo = armRest - 0.15f, upMs = 1000L, holdMs = 0L, downMs = 1000L) }
        signal.rest(1500L)
        val (reps, _) = run(signal)
        assertEquals(5, reps.size)
        for (rep in reps) {
            val closure = abs(rep.first().angles[0] - rep.last().angles[0])
            assertTrue("La rep cierra el ciclo ($closure)", closure <= FIRST_LAST_TOL_RAD)
        }
    }

    @Test
    fun `la ultima rep se cuenta al asentarse sin esperar otra`() {
        val signal = Signal(base = armRest, noiseSigma = 0.015f, step = armStep).rest(2500L)
        repeat(5) { signal.rep(armAmplitude, returnTo = armRest - 0.1f).rest(800L) }
        val (reps, _) = run(signal)
        assertEquals(5, reps.size)
    }

    @Test
    fun `sostener temblando en el extremo no impide contar`() {
        val signal = Signal(base = 2.95f, noiseSigma = 0.015f, step = armStep).rest(2500L)
        repeat(5) { signal.rep(-0.5f, returnTo = 2.95f, upMs = 900L, holdMs = 2000L, downMs = 900L, wobble = 0.35f).rest(1200L) }
        val (reps, listener) = run(signal)
        assertEquals("Rechazos: ${listener.rejections}", 5, reps.size)
    }

    @Test
    fun `calibrar mientras la persona se acomoda espera a que se quede quieta`() {
        val signal = Signal(base = armRest + 0.4f, noiseSigma = 0.015f, step = armStep)
            .move(armRest, 1200L)
            .rest(2500L)
        repeat(5) { signal.rep(armAmplitude, returnTo = armRest).rest(1000L) }
        val (reps, listener) = run(signal)
        assertEquals(1, listener.calibrated)
        assertEquals(5, reps.size)
    }

    @Test
    fun `un acomodo chico en la otra direccion no bloquea las reps reales`() {
        // Se cuenta hasta que llega la primera rep real, que lo deja en evidencia.
        val signal = Signal(base = -2.6f, noiseSigma = 0.01f, step = armStep).rest(2500L)
            .pulse(0.17f, 1000L).rest(1500L)
        repeat(5) { signal.rep(-0.6f, returnTo = -2.6f, upMs = 1000L, holdMs = 500L, downMs = 1000L).rest(1000L) }
        val (reps, listener) = run(signal)
        assertEquals("Rechazos: ${listener.rejections}", 5, reps.size)
        assertEquals(1, listener.retracted)
        assertTrue(RepRejectReason.WRONG_DIRECTION !in listener.rejections)
    }

    @Test
    fun `acomodos chicos antes de la serie se descartan cuando llegan las reps reales`() {
        // Como en el video real "con manos": varios movimientos de ~6° y después reps de ~30°.
        val signal = Signal(base = -2.6f, noiseSigma = 0.01f, step = armStep).rest(2500L)
        repeat(3) { signal.rep(-0.11f, returnTo = -2.6f, upMs = 600L, holdMs = 200L, downMs = 600L).rest(1500L) }
        repeat(5) { signal.rep(-0.5f, returnTo = -2.6f, upMs = 1000L, holdMs = 500L, downMs = 1000L).rest(1000L) }
        val (reps, listener) = run(signal)
        assertEquals(3, listener.retracted)
        assertEquals(5, reps.size)
        assertTrue("Todas las que quedan son reales", reps.all { rep -> rep.minOf { it.angles[0] } < -3.0f })
    }

    @Test
    fun `quien solo se mueve poco no pierde reps por la regla de acomodos`() {
        // Reps chicas y parecidas entre sí: ninguna triplica a las anteriores.
        val signal = Signal(base = 2.95f, noiseSigma = 0.015f, step = armStep).rest(2500L)
        listOf(-0.09f, -0.12f, -0.1f, -0.14f, -0.11f).forEach { amplitude ->
            signal.rep(amplitude, returnTo = 2.95f, upMs = 800L, holdMs = 300L, downMs = 800L).rest(1000L)
        }
        val (reps, listener) = run(signal)
        assertEquals(0, listener.retracted)
        assertEquals("Rechazos: ${listener.rejections}", 5, reps.size)
    }

    @Test
    fun `un reposo nuevo entre reps se toma como cambio de postura y despues cuenta`() {
        // Extensión de rodilla real: arranca en −2.70 y entre reps la pierna queda en −3.20.
        val signal = Signal(base = -2.7f, noiseSigma = 0.015f, step = armStep).rest(2500L)
            .move(-3.2f, 800L).rest(1500L)
        repeat(5) { signal.rep(-0.6f, returnTo = -3.2f, upMs = 1000L, holdMs = 600L, downMs = 1000L).rest(1200L) }
        val (reps, listener) = run(signal)
        assertTrue("Esperaba ≥ 4, contó ${reps.size} (rechazos: ${listener.rejections})", reps.size >= 4)
        assertTrue("No puede contar de más: ${reps.size}", reps.size <= 5)
    }

    // ── Bordes y precondiciones ──────────────────────────────────────────────

    /** Cuadro en el que el conductor no se pudo medir (keypoint ocluido o de baja confianza). */
    private fun invalid(f: AngleFrame) = AngleFrame(
        angles = floatArrayOf(0f),
        valid = booleanArrayOf(false),
        timestampMs = f.timestampMs
    )

    private fun feed(
        segmenter: AdaptiveRestRepetitionSegmenter,
        frames: List<AngleFrame>,
        listener: RecordingListener
    ): List<List<AngleFrame>> {
        frames.forEach { frame -> segmenter.onFrame(frame)?.let { listener.reps.add(it) } }
        return listener.reps
    }

    @Test
    fun `rechaza parametros invalidos`() {
        val l = RecordingListener()
        val casos: List<() -> Any> = listOf(
            { AdaptiveRestRepetitionSegmenter(-1, l) },
            { AdaptiveRestRepetitionSegmenter(0, l, calibrationMs = 0L) },
            { AdaptiveRestRepetitionSegmenter(0, l, minExitRad = 0f) },
            { AdaptiveRestRepetitionSegmenter(0, l, noiseSigmas = 0f) },
            { AdaptiveRestRepetitionSegmenter(0, l, calibrationMs = 2000L, calibrationTimeoutMs = 1000L) },
            { AdaptiveRestRepetitionSegmenter(0, l, maxRepFrames = 1) },
            { AdaptiveRestRepetitionSegmenter(0, l, smoothingAlpha = 0f) },
            { AdaptiveRestRepetitionSegmenter(0, l, smoothingAlpha = 1.5f) }
        )
        casos.forEachIndexed { i, crear ->
            assertThrows("caso $i", IllegalArgumentException::class.java) { crear() }
        }
    }

    @Test
    fun `perder el conductor en las pausas no corta la cuenta`() {
        // 20 fps: 40 cuadros de reposo y después ciclos de 60 (pulso 0..39, pausa 40..59).
        // Se pierde el conductor dos cuadros en mitad de cada pausa y durante la calibración.
        val signal = Signal(noiseSigma = 0.01f).rest(restMs).reps(5, amplitude = 0.3f)
        val frames = signal.frames.mapIndexed { i, f ->
            val enPausa = i >= 40 && (i - 40) % 60 in 45..46
            if (i == 10 || enPausa) invalid(f) else f
        }
        val listener = RecordingListener()
        val reps = feed(AdaptiveRestRepetitionSegmenter(0, listener), frames, listener)
        assertEquals(1, listener.calibrated)
        assertEquals("Rechazos: ${listener.rejections}", 5, reps.size)
    }

    @Test
    fun `un conductor fuera del vector de angulos nunca calibra`() {
        val listener = RecordingListener()
        val segmenter = AdaptiveRestRepetitionSegmenter(driverAngleIndex = 1, listener = listener)
        Signal().rest(restMs).reps(2, amplitude = 0.3f).frames.forEach { assertNull(segmenter.onFrame(it)) }
        assertEquals(0, listener.calibrated)
        assertEquals(0f, segmenter.exitThresholdRad)
    }

    @Test
    fun `un hueco de deteccion en medio de la rep no la corta`() {
        // Pulso del segundo ciclo: cuadros 100..139, extremo en el 119.
        val hueco = 118..121
        val frames = Signal(noiseSigma = 0.01f).rest(restMs).reps(3, amplitude = 0.3f)
            .frames.mapIndexed { i, f -> if (i in hueco) invalid(f) else f }
        val listener = RecordingListener()
        val reps = feed(AdaptiveRestRepetitionSegmenter(0, listener), frames, listener)
        assertEquals("Rechazos: ${listener.rejections}", 3, reps.size)
        // Los cuadros sin conductor viajan dentro de la rep: los demás ángulos pueden servir.
        assertEquals(listOf(0, hueco.count(), 0), reps.map { rep -> rep.count { !it.valid[0] } })
    }

    @Test
    fun `una ventana con pocos cuadros espera a juntar muestras para calibrar`() {
        val listener = RecordingListener()
        val segmenter = AdaptiveRestRepetitionSegmenter(0, listener)
        // Dos cuadros separados por más que la ventana: alcanza el tiempo, no las muestras.
        segmenter.onFrame(frame(2f, 0L))
        segmenter.onFrame(frame(2f, 1600L))
        assertEquals(0, listener.calibrated)
        assertEquals(0f, segmenter.exitThresholdRad)

        var ts = 1650L
        while (ts <= 3600L) {
            segmenter.onFrame(frame(2f, ts))
            ts += 50L
        }
        assertEquals(1, listener.calibrated)
        assertEquals(AdaptiveRestRepetitionSegmenter.MIN_EXIT_RAD, segmenter.exitThresholdRad, 1e-6f)
    }

    @Test
    fun `si nunca se queda quieta calibra al vencer el timeout con el umbral topeado`() {
        // σ = 0.2 rad: aun suavizado, el umbral que saldría (~0.46) no es de alguien quieto.
        val timeout = AdaptiveRestRepetitionSegmenter.CALIBRATION_TIMEOUT_MS
        val frames = Signal(noiseSigma = 0.2f).rest(timeout + 1000L).frames
        val listener = RecordingListener()
        val segmenter = AdaptiveRestRepetitionSegmenter(0, listener)

        frames.filter { it.timestampMs < timeout }.forEach { segmenter.onFrame(it) }
        assertEquals("No debería calibrar moviéndose antes del timeout", 0, listener.calibrated)

        frames.filter { it.timestampMs >= timeout }.forEach { segmenter.onFrame(it) }
        assertEquals(1, listener.calibrated)
        assertEquals(AdaptiveRestRepetitionSegmenter.MAX_EXIT_RAD, segmenter.exitThresholdRad, 1e-6f)
    }

    @Test
    fun `una postura sostenida mas de maxRepFrames pasa a ser el reposo`() {
        // Sube 0.5 y se queda ahí: no vuelve nunca al reposo original, así que la excursión
        // no cierra. Al pasar maxRepFrames se re-ancla y el pulso siguiente cuenta desde ahí.
        val signal = Signal(noiseSigma = 0.01f).rest(restMs)
            .move(2.5f, 600L).rest(5000L)
            .pulse(0.3f, 2000L).rest(1000L)
        val listener = RecordingListener()
        val reps = feed(AdaptiveRestRepetitionSegmenter(0, listener, maxRepFrames = 60), signal.frames, listener)
        assertEquals("Rechazos: ${listener.rejections}", 1, reps.size)
        assertTrue(listener.rejections.isEmpty())
        assertTrue("La rep es el pulso desde la postura nueva", reps.single().all { it.angles[0] > 2.3f })
    }

    @Test
    fun `sin suavizado tambien cuenta`() {
        val listener = RecordingListener()
        val segmenter = AdaptiveRestRepetitionSegmenter(0, listener, smoothingAlpha = 1f)
        val reps = feed(segmenter, Signal(noiseSigma = 0.01f).rest(restMs).reps(3, amplitude = 0.3f).frames, listener)
        assertEquals("Rechazos: ${listener.rejections}", 3, reps.size)
    }
}
