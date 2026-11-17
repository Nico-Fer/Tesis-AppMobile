package com.steadypath.app.domain.comparison

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Segmentador para GRABAR referencias, de todos los ejercicios. La referencia existe para
 * personalizar las correcciones, así que cuenta lo que la persona puede hacer: quien apenas
 * baja en la sentadilla o sube los brazos a medias tiene que poder grabarla igual. No hay
 * amplitud mínima; el único piso es distinguir el movimiento del temblor de la pose.
 *
 * Reemplaza al segmentador anclado al reposo, que cerraba la rep solo al volver al reposo
 * calibrado con unos pocos grados de tolerancia (parado es un tope y eso se cumple solo,
 * con brazos o piernas no) y exigía un extremo de 1.5× el umbral. Con keypoints reales de
 * modelosCV contaba 1 de 2 en brazos, 5 de 7 en talones y 0–2 de 5 en extensión de rodilla.
 *
 * 1. **Calibración con quietud**: mediana y MAD del conductor sobre una ventana deslizante
 *    de [calibrationMs]. Se acepta recién cuando el umbral que saldría es de alguien quieto
 *    (≤ [STILL_EXIT_RAD]); quien toca «Comenzar» y después se acomoda calibraba moviéndose,
 *    con el reposo corrido y el umbral al tope. Pasado [calibrationTimeoutMs] se acepta lo
 *    que haya, con el clamp [MIN_EXIT_RAD]..[MAX_EXIT_RAD].
 * 2. **Reposo**: la media deriva lento (absorbe acomodos posturales); superar el umbral de
 *    salida, `max(4σ, MIN_EXIT_RAD)`, abre una excursión. Ese umbral es todo lo que se le
 *    pide a la amplitud.
 * 3. **Excursión**: se sigue el extremo. Cierra:
 *    - al volver al reposo calibrado (`dev ≤ reentry`);
 *    - en el **valle**, si después de bajar cerca del reposo ya arrancó la siguiente
 *      (reps encadenadas, sin pausa abajo);
 *    - al **asentarse** cerca del reposo: así cuenta la última sin esperar otra.
 *    Si en cambio se asienta lejos del reposo después de haber bajado, fue un cambio de
 *    postura (la pierna que queda levantada entre reps): se descarta sin contar. En los
 *    cierres por valle y por asiento el reposo se re-ancla donde quedó la persona.
 *
 * "Cerca del reposo" es relativo al extremo y nunca más que [FIRST_LAST_TOL_RAD]: una rep
 * que no cierra el ciclo dentro de esa tolerancia fallaría después al subir la referencia.
 *
 * Sin amplitud mínima, un acomodo lento cuenta igual que una rep chica. Lo único que los
 * separa es lo que viene después: si llega una rep [SUPERSEDE_FACTOR] veces más grande que
 * todo lo contado, lo anterior eran acomodos y se descarta. Sin esto, cuatro acomodos y la
 * primera rep real cerraban la toma y el medoide salía de un acomodo.
 * La forma se valida con [RepShapeGate.score] (acepta pausa en el extremo) más
 * [RepShapeGate.peakCount] (rechaza reps pegadas).
 *
 * Mismo unwrap y EMA causal que [PeakValleyRepetitionSegmenter]: el detector ve el conductor
 * suavizado, el buffer guarda los cuadros crudos.
 */
class AdaptiveRestRepetitionSegmenter(
    private val driverAngleIndex: Int,
    private val listener: RecordingSegmenterListener,
    private val calibrationMs: Long = CALIBRATION_MS,
    private val calibrationTimeoutMs: Long = CALIBRATION_TIMEOUT_MS,
    private val noiseSigmas: Float = NOISE_SIGMAS,
    private val minExitRad: Float = MIN_EXIT_RAD,
    private val minRepDurationMs: Long = MIN_REP_DURATION_MS,
    private val minShapeNcc: Double = MIN_SHAPE_NCC,
    private val settleMs: Long = SETTLE_MS,
    private val maxRepFrames: Int = MAX_REP_FRAMES,
    private val smoothingAlpha: Float = SMOOTHING_ALPHA
) : RepetitionSegmenter {

    private enum class Phase { CALIBRATING, REST, EXCURSION }

    private val buffer = ArrayList<AngleFrame>()

    /** Conductor des-envuelto y suavizado, paralelo a [buffer]; NaN = conductor inválido. */
    private val values = ArrayList<Float>()

    private var phase = Phase.CALIBRATING
    private var initialized = false
    private var lastUnwrapped = 0f
    private var smoothedInit = false
    private var smoothedValue = 0f

    private var calibStartTs = -1L
    private val calibTimestamps = ArrayList<Long>()
    private val calibSamples = ArrayList<Float>()

    private var restMean = 0f
    private var exitRad = 0f
    private var reentryRad = 0f

    private var sign = 0
    private var extremeDev = 0f

    // Vuelta hacia el reposo: valle más bajo y ancla de quietud para detectar el asiento.
    private var returning = false
    private var valleyDev = 0f
    private var valleyIdx = 0
    private var stillDev = 0f
    private var stillSinceTs = 0L

    /** Dirección de las repeticiones aceptadas; 0 hasta que haya una. */
    private var acceptedSign = 0

    /** Reps aceptadas desde el arranque o el último descarte por [SUPERSEDE_FACTOR]. */
    private var acceptedCount = 0

    /** Mayor extremo aceptado: decide si una rep nueva deja en evidencia que lo anterior eran acomodos. */
    private var maxAcceptedExtreme = 0f

    init {
        require(driverAngleIndex >= 0) { "driverAngleIndex debe ser ≥ 0" }
        require(calibrationMs > 0 && minExitRad > 0f && noiseSigmas > 0f) { "Umbrales deben ser positivos" }
        require(calibrationTimeoutMs >= calibrationMs) { "El timeout no puede ser menor que la calibración" }
        require(maxRepFrames > 1) { "maxRepFrames debe ser > 1" }
        require(smoothingAlpha > 0f && smoothingAlpha <= 1f) { "smoothingAlpha debe estar en (0, 1]" }
    }

    /** Umbral de salida vigente (0 hasta calibrar); expuesto para tests y diagnóstico. */
    val exitThresholdRad: Float get() = exitRad

    override fun onFrame(frame: AngleFrame): List<AngleFrame>? {
        buffer.add(frame)
        val driverValid = driverAngleIndex < frame.valid.size && frame.valid[driverAngleIndex]
        if (!driverValid) {
            values.add(Float.NaN)
            if (phase != Phase.EXCURSION) trimToLast()
            return null
        }
        val v = smooth(unwrap(frame.angles[driverAngleIndex]))
        values.add(v)
        val ts = frame.timestampMs
        return when (phase) {
            Phase.CALIBRATING -> { calibrate(v, ts); null }
            Phase.REST -> { observeRest(v); null }
            Phase.EXCURSION -> observeExcursion(v, ts, buffer.size - 1)
        }
    }

    override fun reset() {
        buffer.clear()
        values.clear()
        calibTimestamps.clear()
        calibSamples.clear()
        calibStartTs = -1L
        phase = Phase.CALIBRATING
        initialized = false
        smoothedInit = false
        sign = 0
        acceptedSign = 0
        acceptedCount = 0
        maxAcceptedExtreme = 0f
        returning = false
        exitRad = 0f
    }

    private fun calibrate(v: Float, ts: Long) {
        if (calibStartTs < 0) calibStartTs = ts
        calibTimestamps.add(ts)
        calibSamples.add(v)
        // Ventana deslizante: se descarta lo viejo mientras quede al menos calibrationMs.
        while (calibTimestamps.size > 1 && ts - calibTimestamps[1] >= calibrationMs) {
            calibTimestamps.removeAt(0)
            calibSamples.removeAt(0)
        }
        if (ts - calibTimestamps.first() < calibrationMs || calibSamples.size < MIN_CALIBRATION_SAMPLES) return

        val sorted = calibSamples.sorted()
        val median = sorted[sorted.size / 2]
        val mad = calibSamples.map { abs(it - median) }.sorted()[sorted.size / 2]
        val rawExit = noiseSigmas * mad * MAD_TO_SIGMA
        // Moviéndose todavía: se espera a que se quede quieta, con un tope de tiempo.
        if (rawExit > STILL_EXIT_RAD && ts - calibStartTs < calibrationTimeoutMs) return

        restMean = median
        exitRad = max(rawExit, minExitRad).coerceAtMost(MAX_EXIT_RAD)
        reentryRad = REENTRY_FRACTION * exitRad
        calibTimestamps.clear()
        calibSamples.clear()
        phase = Phase.REST
        trimToLast()
        listener.onDiagnostic("calibrado: reposo=%.3f umbral=%.3f".format(restMean, exitRad))
        listener.onCalibrated()
    }

    private fun observeRest(v: Float) {
        val dev = v - restMean
        if (abs(dev) >= exitRad) {
            phase = Phase.EXCURSION
            sign = if (dev > 0f) 1 else -1
            extremeDev = abs(dev)
            returning = false
        } else {
            restMean += REST_DRIFT_ALPHA * dev
            trimToLast()
        }
    }

    private fun observeExcursion(v: Float, ts: Long, idx: Int): List<AngleFrame>? {
        // Demasiado tiempo sin cerrar (postura sostenida, vaivén sin asentarse): ese es el
        // nuevo reposo.
        if (buffer.size > maxRepFrames) {
            listener.onDiagnostic("excursión sin cerrar en $maxRepFrames cuadros, se re-ancla")
            restMean = v
            phase = Phase.REST
            returning = false
            trimToLast()
            return null
        }
        val dev = sign * (v - restMean)
        val returnLimit = RETURN_FRACTION * extremeDev
        val closeLimit = min(returnLimit, FIRST_LAST_TOL_RAD)
        val nearLimit = max(reentryRad, min(NEAR_RETURN_FRACTION * extremeDev, closeLimit))

        if (dev <= reentryRad) return closeAt(idx, reanchor = false, how = "reposo")

        if (dev <= returnLimit) {
            if (!returning) {
                returning = true
                valleyDev = dev
                valleyIdx = idx
                stillDev = dev
                stillSinceTs = ts
                return null
            }
            if (dev < valleyDev) {
                valleyDev = dev
                valleyIdx = idx
            }
            // Ya arrancó la siguiente: la rep cierra en el valle, no acá.
            if (valleyDev <= nearLimit && dev >= valleyDev + reentryRad) {
                return closeAt(valleyIdx, reanchor = true, how = "valle")
            }
            if (abs(dev - stillDev) > exitRad) {
                stillDev = dev
                stillSinceTs = ts
            } else if (ts - stillSinceTs >= settleMs) {
                if (dev <= closeLimit) return closeAt(idx, reanchor = true, how = "asentado")
                shiftRest(v, idx, dev)
            }
            return null
        }

        // Volvió a subir desde un valle cercano al reposo (subida rápida, en un solo cuadro).
        if (returning && valleyDev <= nearLimit) return closeAt(valleyIdx, reanchor = true, how = "valle")
        returning = false
        if (dev > extremeDev) extremeDev = dev
        return null
    }

    /**
     * Cierra la excursión en [closeIdx]: valida con el reposo vigente y, si [reanchor], lo
     * mueve a donde quedó la persona. El cuadro de cierre se comparte con la próxima.
     */
    private fun closeAt(closeIdx: Int, reanchor: Boolean, how: String): List<AngleFrame>? {
        phase = Phase.REST
        returning = false
        val deviation = deviationSeries(closeIdx)
        val extreme = deviation.maxOrNull() ?: 0f
        val duration = buffer[closeIdx].timestampMs - buffer.first().timestampMs
        val longEnough = duration >= minRepDurationMs
        val newRest = values[closeIdx]

        // Breve y apenas por encima del umbral = pico de ruido, no un intento: se descarta en
        // silencio. Si duró lo suficiente, cuenta por chica que sea: no hay amplitud mínima.
        if (!longEnough && extreme < NOISE_MARGIN_FACTOR * exitRad) {
            dropThrough(closeIdx)
            return null
        }
        val shape = RepShapeGate.score(deviation)
        val peaks = RepShapeGate.peakCount(deviation)
        // Una rep mucho más grande que todo lo aceptado es el ejercicio de verdad: lo anterior
        // eran acomodos, en cualquier dirección. Con keypoints reales, la persona se acomodaba
        // la pierna con movimientos de 5–9° y después hacía reps de 22–32°.
        val supersedes = acceptedCount > 0 && extreme >= SUPERSEDE_FACTOR * maxAcceptedExtreme
        val wrongDirection = acceptedSign != 0 && sign != acceptedSign && !supersedes
        val reason = when {
            !longEnough -> RepRejectReason.TOO_SHORT
            wrongDirection -> RepRejectReason.WRONG_DIRECTION
            shape < minShapeNcc || peaks != 1 -> RepRejectReason.BAD_SHAPE
            else -> null
        }
        listener.onDiagnostic(
            "cierre por %s: extremo=%.3f umbral=%.3f duración=%dms forma=%.2f picos=%d → %s".format(
                how, extreme, exitRad, duration, shape, peaks, reason ?: "cuenta"
            )
        )
        if (reanchor && !newRest.isNaN()) restMean = newRest

        if (reason != null) {
            listener.onRejected(reason)
            dropThrough(closeIdx)
            return null
        }
        if (supersedes) {
            listener.onDiagnostic(
                "esta rep supera ${SUPERSEDE_FACTOR}× lo aceptado: se descartan $acceptedCount como acomodos"
            )
            listener.onRetracted(acceptedCount)
            acceptedCount = 0
            maxAcceptedExtreme = 0f
        }
        acceptedSign = sign
        acceptedCount++
        maxAcceptedExtreme = max(maxAcceptedExtreme, extreme)
        val repetition = ArrayList(buffer.subList(0, closeIdx + 1))
        // Sin recortar: si cerró en el valle, lo posterior es el arranque de la siguiente.
        dropThrough(closeIdx, trim = false)
        return repetition
    }

    /** Cambio de postura: se asentó lejos del reposo. No es una rep; ese es el reposo nuevo. */
    private fun shiftRest(v: Float, idx: Int, dev: Float) {
        listener.onDiagnostic("cambio de postura: quedó a %.3f del reposo, se re-ancla".format(dev))
        restMean = v
        phase = Phase.REST
        returning = false
        dropThrough(idx)
    }

    private fun deviationSeries(lastIdx: Int): List<Float> {
        val out = ArrayList<Float>(lastIdx + 1)
        for (i in 0..lastIdx) {
            val value = values[i]
            if (!value.isNaN()) out.add(sign * (value - restMean))
        }
        return out
    }

    /** Descarta todo antes de [idx]: [idx] queda como primer cuadro del reposo. */
    private fun dropThrough(idx: Int, trim: Boolean = true) {
        val tailFrames = ArrayList(buffer.subList(idx, buffer.size))
        val tailValues = ArrayList(values.subList(idx, values.size))
        buffer.clear()
        values.clear()
        buffer.addAll(tailFrames)
        values.addAll(tailValues)
        if (trim) trimToLast()
    }

    /** En reposo se retienen los últimos cuadros: la rep arranca antes del cruce de salida. */
    private fun trimToLast() {
        if (buffer.size <= REST_TAIL_FRAMES) return
        val tailFrames = ArrayList(buffer.subList(buffer.size - REST_TAIL_FRAMES, buffer.size))
        val tailValues = ArrayList(values.subList(values.size - REST_TAIL_FRAMES, values.size))
        buffer.clear()
        values.clear()
        buffer.addAll(tailFrames)
        values.addAll(tailValues)
    }

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
            return raw
        }
        val delta = atan2(
            sin((raw - lastUnwrapped).toDouble()),
            cos((raw - lastUnwrapped).toDouble())
        ).toFloat()
        lastUnwrapped += delta
        return lastUnwrapped
    }

    companion object {
        // TODO: calibrar con datos — afinados contra keypoints reales de modelosCV (talones,
        // extensión de rodilla) y señales simuladas de brazos; ver docs/referencias-dtw-en-app.md.

        /** Quieto en la posición inicial antes de empezar a contar. */
        const val CALIBRATION_MS = 1500L
        const val MIN_CALIBRATION_SAMPLES = 8

        /** Si en este tiempo no se quedó quieta, se calibra con lo que haya. */
        const val CALIBRATION_TIMEOUT_MS = 6000L

        /** Umbral de salida por debajo del cual la ventana de calibración se considera quieta. */
        const val STILL_EXIT_RAD = 0.12f

        /** MAD → σ para ruido gaussiano. */
        const val MAD_TO_SIGMA = 1.4826f

        /** Salir del reposo exige superar este múltiplo del ruido medido... */
        const val NOISE_SIGMAS = 4f

        /** ...y nunca menos de ~4.6°, por si el reposo calibró demasiado limpio. */
        const val MIN_EXIT_RAD = 0.08f

        /** Tope por si la persona se movió durante la calibración. */
        const val MAX_EXIT_RAD = 0.25f

        const val REENTRY_FRACTION = 0.5f

        /**
         * Una excursión breve que no supera este múltiplo del umbral es ruido y no se avisa.
         * No es un mínimo para contar: las que duran [MIN_REP_DURATION_MS] cuentan igual.
         */
        const val NOISE_MARGIN_FACTOR = 1.5f

        /** Un movimiento voluntario ida y vuelta no dura menos que esto. */
        const val MIN_REP_DURATION_MS = 600L

        /** NCC mínima contra la mejor plantilla de [RepShapeGate.score]. */
        const val MIN_SHAPE_NCC = 0.6

        /** Bajó al menos esta fracción desde el extremo: empieza a buscar el valle. */
        const val RETURN_FRACTION = 0.5f

        /** Un valle hasta esta fracción del extremo cuenta como "volvió al reposo". */
        const val NEAR_RETURN_FRACTION = 0.3f

        /** Quieto este tiempo en la vuelta = asentado. */
        const val SETTLE_MS = 500L

        /**
         * Una rep este múltiplo más grande que todo lo aceptado lo reemplaza: lo anterior
         * eran acomodos y se descarta ([RecordingSegmenterListener.onRetracted]). Quien solo
         * puede moverse poco nunca dispara esto, porque todas sus reps son parecidas.
         */
        const val SUPERSEDE_FACTOR = 3f

        /**
         * Deriva de la media en reposo por cuadro (~10 s de constante de tiempo a 20 fps).
         * Más rápida se come el arranque de una excursión lenta y chica antes de que salga.
         */
        const val REST_DRIFT_ALPHA = 0.005f

        /** EMA del detector: la excursión chica tiene ruido de pose proporcionalmente grande. */
        const val SMOOTHING_ALPHA = 0.5f

        /** ~30 s a 20 fps sin volver al reposo → la postura actual pasa a ser el reposo. */
        const val MAX_REP_FRAMES = 600

        /** Cuadros de reposo que preceden a la salida dentro de la rep (el EMA detecta con retraso). */
        const val REST_TAIL_FRAMES = 3
    }
}
