package com.steadypath.app.domain.comparison

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Segmentador por picos/valles del ángulo conductor, con detección online (causal):
 *
 * - **Histéresis**: un extremo se confirma recién cuando la señal retrocede
 *   [hysteresisRad] desde el candidato — el ruido menor no genera extremos.
 * - **Prominencia mínima**: el pico debe superar al valle de arranque en
 *   [minProminenceRad]; oscilaciones chicas no cuentan como repetición.
 * - **Distancia mínima entre picos**: [minPeakGapMs] evita el doble conteo.
 *
 * Una repetición es el ciclo valle → pico → valle sobre el ángulo conductor. Como la
 * confirmación del valle de cierre requiere el retroceso de histéresis, los cuadros de ese
 * retroceso pertenecen a la repetición siguiente: al cerrar, el buffer se parte en el valle
 * y el cuadro del valle queda compartido entre ambas repeticiones (ambos segmentos cubren
 * el ciclo completo). Consecuencia inherente: una repetición se cierra cuando la siguiente
 * empieza a subir.
 *
 * El ángulo conductor se des-envuelve (unwrap) muestra a muestra para que un ejercicio que
 * cruce ±π no genere saltos falsos. Los cuadros con conductor inválido se acumulan en el
 * buffer (participan del DTW con su máscara) pero no actualizan el detector.
 */
class PeakValleyRepetitionSegmenter(
    private val driverAngleIndex: Int,
    // TODO: calibrar con datos — todos los parámetros de detección son placeholder.
    private val minProminenceRad: Float = DEFAULT_MIN_PROMINENCE_RAD,
    private val hysteresisRad: Float = DEFAULT_HYSTERESIS_RAD,
    private val minPeakGapMs: Long = DEFAULT_MIN_PEAK_GAP_MS,
    private val maxRepFrames: Int = DEFAULT_MAX_REP_FRAMES,
    /** EMA sobre el conductor; 1 = sin suavizado. Solo el detector lo ve, el DTW no. */
    private val smoothingAlpha: Float = 1f
) : RepetitionSegmenter {

    private enum class Phase { ASCENDING, DESCENDING }

    private val buffer = ArrayList<AngleFrame>()

    /** Valores des-envueltos del conductor, paralelo a [buffer]; NaN = conductor inválido. */
    private val driverValues = ArrayList<Float>()

    private var initialized = false
    private var lastUnwrapped = 0f
    private var phase = Phase.ASCENDING
    private var repStartMin = 0f
    private var candMax = 0f
    private var candMaxTs = 0L
    private var candMin = 0f
    private var candMinIdx = -1
    private var lastPeakTs = Long.MIN_VALUE / 2

    init {
        require(driverAngleIndex >= 0) { "driverAngleIndex debe ser ≥ 0" }
        require(minProminenceRad > 0f && hysteresisRad > 0f) { "Umbrales deben ser positivos" }
        require(maxRepFrames > 1) { "maxRepFrames debe ser > 1" }
        require(smoothingAlpha > 0f && smoothingAlpha <= 1f) { "smoothingAlpha debe estar en (0, 1]" }
    }

    override fun onFrame(frame: AngleFrame): List<AngleFrame>? {
        buffer.add(frame)
        var closed: List<AngleFrame>? = null

        val driverValid = driverAngleIndex < frame.valid.size && frame.valid[driverAngleIndex]
        if (driverValid) {
            val v = smooth(unwrap(frame.angles[driverAngleIndex]))
            driverValues.add(v)
            closed = update(v, frame.timestampMs, buffer.size - 1)
        } else {
            driverValues.add(Float.NaN)
        }

        // Sin ciclo detectable en toda la ventana → descartar para acotar memoria.
        if (closed == null && buffer.size > maxRepFrames) {
            clearWindow()
        }
        return closed
    }

    override fun reset() {
        clearWindow()
        initialized = false
        smoothedInit = false
        lastPeakTs = Long.MIN_VALUE / 2
    }

    private fun clearWindow() {
        buffer.clear()
        driverValues.clear()
        phase = Phase.ASCENDING
        candMinIdx = -1
        if (initialized) {
            repStartMin = lastUnwrapped
            candMax = lastUnwrapped
        }
    }

    private var smoothedInit = false
    private var smoothedValue = 0f

    /** EMA causal: atenúa los picos de ruido de pose sin desfasar movimientos lentos. */
    private fun smooth(v: Float): Float {
        if (smoothingAlpha >= 1f) return v
        if (!smoothedInit) {
            smoothedInit = true
            smoothedValue = v
            return v
        }
        smoothedValue += smoothingAlpha * (v - smoothedValue)
        return smoothedValue
    }

    private fun unwrap(raw: Float): Float {
        if (!initialized) {
            initialized = true
            lastUnwrapped = raw
            repStartMin = raw
            candMax = raw
            return raw
        }
        val delta = atan2(
            sin((raw - lastUnwrapped).toDouble()),
            cos((raw - lastUnwrapped).toDouble())
        ).toFloat()
        lastUnwrapped += delta
        return lastUnwrapped
    }

    private fun update(v: Float, ts: Long, idx: Int): List<AngleFrame>? {
        when (phase) {
            Phase.ASCENDING -> {
                if (v < repStartMin) repStartMin = v
                if (v > candMax) {
                    candMax = v
                    candMaxTs = ts
                } else if (
                    candMax - v >= hysteresisRad &&
                    candMax - repStartMin >= minProminenceRad &&
                    candMaxTs - lastPeakTs >= minPeakGapMs
                ) {
                    phase = Phase.DESCENDING
                    lastPeakTs = candMaxTs
                    candMin = v
                    candMinIdx = idx
                }
            }

            Phase.DESCENDING -> {
                if (v < candMin) {
                    candMin = v
                    candMinIdx = idx
                } else if (
                    v - candMin >= hysteresisRad &&
                    candMax - candMin >= minProminenceRad
                ) {
                    return closeRepetition()
                }
            }
        }
        return null
    }

    private fun closeRepetition(): List<AngleFrame> {
        val repetition = ArrayList(buffer.subList(0, candMinIdx + 1))

        // El cuadro del valle se comparte: cierra esta repetición y abre la siguiente.
        val carriedFrames = ArrayList(buffer.subList(candMinIdx, buffer.size))
        val carriedValues = ArrayList(driverValues.subList(candMinIdx, driverValues.size))
        buffer.clear()
        buffer.addAll(carriedFrames)
        driverValues.clear()
        driverValues.addAll(carriedValues)

        phase = Phase.ASCENDING
        repStartMin = candMin
        candMax = candMin
        candMaxTs = buffer.first().timestampMs
        candMinIdx = -1

        // Replay del retroceso: esos cuadros ya son parte de la próxima repetición. Un
        // cierre durante el replay es imposible (la fase recién se reseteó a ASCENDING
        // y el retroceso es más corto que un ciclo), se ignora el retorno.
        for (i in 1 until buffer.size) {
            val value = driverValues[i]
            if (!value.isNaN()) update(value, buffer[i].timestampMs, i)
        }

        return repetition
    }

    companion object {
        /**
         * Umbrales derivados de la amplitud del conductor en la referencia: una sentadilla
         * parcial (~0.4 rad) exige mucho menos excursión que un levantamiento de brazos
         * (~1.4 rad) — con el default fijo, la primera no contaría reps apenas más
         * superficiales que la referencia. Sin amplitud (flujo de captura) rigen los defaults.
         */
        fun tunedFor(driverAngleIndex: Int, driverAmplitudeRad: Float?): PeakValleyRepetitionSegmenter {
            if (driverAmplitudeRad == null || driverAmplitudeRad <= 0f) {
                return PeakValleyRepetitionSegmenter(driverAngleIndex)
            }
            // TODO: calibrar con datos — fracciones placeholder.
            return PeakValleyRepetitionSegmenter(
                driverAngleIndex = driverAngleIndex,
                // El piso no puede superar una fracción de la amplitud: ejercicios de
                // excursión chica (elevación de talones ~0.17 rad) quedarían incontables.
                minProminenceRad = max(PROMINENCE_FRACTION * driverAmplitudeRad, MIN_PROMINENCE_RAD)
                    .coerceAtMost(MAX_PROMINENCE_AMPLITUDE_FRACTION * driverAmplitudeRad),
                hysteresisRad = (HYSTERESIS_FRACTION * driverAmplitudeRad)
                    .coerceIn(MIN_HYSTERESIS_RAD, DEFAULT_HYSTERESIS_RAD),
                // Excursión chica = ruido de pose proporcionalmente grande: EMA en el
                // detector (evita dobles conteos); los amplios (hombros) quedan sin tocar.
                smoothingAlpha = if (driverAmplitudeRad < SMOOTHING_AMPLITUDE_CUTOFF_RAD) {
                    SMALL_AMPLITUDE_SMOOTHING_ALPHA
                } else {
                    1f
                }
            )
        }

        /**
         * Fracción de la amplitud de referencia exigida para contar una repetición.
         * Baja a propósito: una rep muy corta debe CONTAR y recibir hint de UNDERSHOOT —
         * si no cierra segmento, el paciente no recibe feedback alguno.
         */
        const val PROMINENCE_FRACTION = 0.25f

        /** Piso de prominencia (~8.6°): por debajo, el ruido de pose contaría reps. */
        const val MIN_PROMINENCE_RAD = 0.15f

        /** El piso se capea a esta fracción de la amplitud en ejercicios de excursión chica. */
        const val MAX_PROMINENCE_AMPLITUDE_FRACTION = 0.5f

        const val HYSTERESIS_FRACTION = 0.25f
        const val MIN_HYSTERESIS_RAD = 0.05f

        /** Por debajo de esta amplitud de referencia, el detector suaviza el conductor. */
        const val SMOOTHING_AMPLITUDE_CUTOFF_RAD = 0.6f
        const val SMALL_AMPLITUDE_SMOOTHING_ALPHA = 0.5f

        /** ~20° de excursión mínima del ángulo conductor para que cuente como repetición. */
        const val DEFAULT_MIN_PROMINENCE_RAD = 0.35f

        /** Retroceso que confirma un extremo (~8.6°); el ruido típico queda por debajo. */
        const val DEFAULT_HYSTERESIS_RAD = 0.15f

        /** Dos picos a menos de esto son la misma repetición. */
        const val DEFAULT_MIN_PEAK_GAP_MS = 800L

        /** ~30 s a 20 fps sin repetición detectada → se descarta la ventana. */
        const val DEFAULT_MAX_REP_FRAMES = 600
    }
}
