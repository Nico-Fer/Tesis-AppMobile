package com.steadypath.app.data.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.steadypath.app.domain.model.PoseFrame
import java.io.File
import java.io.FileOutputStream

/**
 * Log de diagnóstico del decodificador de pose, pensado para responder desde logcat **por
 * qué no se dibuja el esqueleto**.
 *
 * Los logs de [YoloPoseEstimator] confirman que el intérprete carga y que la inferencia
 * corre, pero no distinguen entre "no hay detección", "los keypoints no superan el umbral de
 * confianza" y "las coordenadas están en otra escala". Esta clase imprime justamente eso.
 *
 * Vive acá y no en [PosePostprocessor] porque ese decodificador es **puro** (se testea en la
 * JVM) y no puede importar `android.util.Log`. Se invoca cada N frames desde el estimador,
 * que ya tiene a mano la salida cruda, el letterbox y el [PoseFrame] resultante.
 */
internal object PoseDiagnostics {

    /**
     * Imprime una foto del frame actual. [output] debe ser la salida **cruda** del modelo:
     * las coordenadas se loguean sin escalar ni des-letterboxear, porque su magnitud es
     * precisamente lo que revela el [PoseModelSpec.CoordinateSpace] real del export.
     */
    fun log(
        spec: PoseModelSpec,
        output: FloatArray,
        params: LetterboxParams,
        sourceWidth: Int,
        sourceHeight: Int,
        frame: PoseFrame?
    ) {
        val detectionCount = output.size / spec.outputChannels

        var bestIndex = -1
        var bestScore = Float.NEGATIVE_INFINITY
        var aboveThreshold = 0
        for (d in 0 until detectionCount) {
            val score = output[spec.indexOf(PoseModelSpec.SCORE_CHANNEL, d, detectionCount)]
            if (score > bestScore) {
                bestScore = score
                bestIndex = d
            }
            if (score >= spec.confidenceThreshold) aboveThreshold++
        }

        if (bestIndex < 0) {
            Log.w(TAG, "Salida sin detecciones ($detectionCount anchors). Revisá el preprocesado.")
            return
        }

        val cx = output[spec.indexOf(0, bestIndex, detectionCount)]
        val cy = output[spec.indexOf(1, bestIndex, detectionCount)]
        val w = output[spec.indexOf(2, bestIndex, detectionCount)]
        val h = output[spec.indexOf(3, bestIndex, detectionCount)]

        val kp0 = PoseModelSpec.FIRST_KEYPOINT_CHANNEL
        val noseX = output[spec.indexOf(kp0, bestIndex, detectionCount)]
        val noseY = output[spec.indexOf(kp0 + 1, bestIndex, detectionCount)]
        val noseConf = output[spec.indexOf(kp0 + 2, bestIndex, detectionCount)]

        // Veredicto de escala. El umbral es holgado a propósito y NO 1.0: un modelo que
        // normaliza se pasa de 1.0 cuando la persona toca el borde y la caja se extrapola
        // fuera de cuadro (medido: h=1.003 con el sujeto llenando el frame), y un corte
        // exacto daba falsos positivos que pedían romper un `coordinateSpace` correcto.
        // Entre ambas escalas hay dos órdenes de magnitud, así que cualquier corte
        // intermedio las separa sin ambigüedad.
        val looksNormalized = maxOf(cx, cy, w, h) <= NORMALIZED_MAX
        val verdict = if (looksNormalized) "NORMALIZED" else "PIXELS(0..${spec.inputSize})"

        Log.d(
            TAG,
            "src=${sourceWidth}x$sourceHeight " +
                "letterbox(scale=${f(params.scale)} padX=${f(params.padX)} padY=${f(params.padY)}) | " +
                "detecciones>=${spec.confidenceThreshold}: $aboveThreshold/$detectionCount " +
                "scoreMax=${f(bestScore)} | " +
                "CRUDO box=[${f(cx)} ${f(cy)} ${f(w)} ${f(h)}] " +
                "nariz=[${f(noseX)} ${f(noseY)}] conf=${f(noseConf)}"
        )

        if (looksNormalized != (spec.coordinateSpace == PoseModelSpec.CoordinateSpace.NORMALIZED)) {
            Log.e(
                TAG,
                "DESAJUSTE DE ESCALA: la salida cruda parece $verdict pero el spec declara " +
                    "${spec.coordinateSpace}. Corregí `coordinateSpace` en PoseModelSpec.ACTIVE."
            )
        }

        // Estructura cruda completa de los 17 keypoints del mejor candidato, ANTES de
        // cualquier transformación del pipeline. Compara sus rangos contra el box en el
        // mismo espacio crudo del tensor: si los x se apiñan mientras los y se despliegan
        // como el box, la cabeza de keypoints del modelo emite colapsado en x — y el
        // pipeline queda descartado por construcción.
        var minKx = Float.MAX_VALUE
        var maxKx = -Float.MAX_VALUE
        var minKy = Float.MAX_VALUE
        var maxKy = -Float.MAX_VALUE
        val dump = StringBuilder("KP crudos [i x y conf]:")
        for (k in 0 until spec.keypointCount) {
            val base = kp0 + k * PoseModelSpec.KEYPOINT_STRIDE
            val x = output[spec.indexOf(base, bestIndex, detectionCount)]
            val y = output[spec.indexOf(base + 1, bestIndex, detectionCount)]
            val c = output[spec.indexOf(base + 2, bestIndex, detectionCount)]
            if (c >= VISIBLE_CONFIDENCE) {
                if (x < minKx) minKx = x
                if (x > maxKx) maxKx = x
                if (y < minKy) minKy = y
                if (y > maxKy) maxKy = y
            }
            dump.append(" [$k ${f(x)} ${f(y)} ${f(c)}]")
        }
        Log.d(TAG, dump.toString())
        if (maxKx >= minKx) {
            Log.d(
                TAG,
                "Rango crudo: kpts x=[${f(minKx)},${f(maxKx)}] y=[${f(minKy)},${f(maxKy)}] | " +
                    "box x=[${f(cx - w / 2f)},${f(cx + w / 2f)}] " +
                    "y=[${f(cy - h / 2f)},${f(cy + h / 2f)}]"
            )
        }

        if (frame == null) {
            Log.w(
                TAG,
                "process() devolvió null → no hay nada que dibujar. Ninguna detección superó " +
                    "confidenceThreshold=${spec.confidenceThreshold} (scoreMax=${f(bestScore)})."
            )
            return
        }

        val total = frame.keypoints.size
        val confident = frame.keypoints.count { it.confidence >= VISIBLE_CONFIDENCE }
        val inFrame = frame.keypoints.count { it.x in 0f..1f && it.y in 0f..1f }
        val nose = frame.keypoints.firstOrNull()

        Log.d(
            TAG,
            "PoseFrame score=${f(frame.score)} | " +
                "conf>=$VISIBLE_CONFIDENCE: $confident/$total | " +
                "dentro de cuadro: $inFrame/$total | " +
                "nariz normalizada=(${f(nose?.x ?: 0f)}, ${f(nose?.y ?: 0f)})"
        )

        // Dispersión de los keypoints contra el box. Si el esqueleto se ve achatado en un
        // eje, acá aparece como un ancho/alto que no cuadra con la caja.
        //
        // OJO con interpretarlo como métrica de calidad del modelo: el denominador es el
        // box **predicho**, y un modelo roto también predice mal el box —típicamente lo
        // sobreestima—, lo que infla el denominador y hace que el ratio **subestime** el
        // daño. Sirve como señal en vivo sin ground truth; para medir de verdad hay que
        // normalizar contra el box anotado, offline y contra un dataset con GT.
        val visibleKps = frame.keypoints.filter { it.confidence >= VISIBLE_CONFIDENCE }
        if (visibleKps.isNotEmpty()) {
            val kpW = (visibleKps.maxOf { it.x } - visibleKps.minOf { it.x })
            val kpH = (visibleKps.maxOf { it.y } - visibleKps.minOf { it.y })
            // Box crudo → mismo espacio normalizado del frame, para comparar manzanas con
            // manzanas: se escala a píxeles de modelo, se des-letterboxea y se normaliza.
            val boxW = w * spec.coordinateScale / params.scale / sourceWidth
            val boxH = h * spec.coordinateScale / params.scale / sourceHeight
            Log.d(
                TAG,
                "Dispersion keypoints=${f(kpW)}x${f(kpH)} vs box=${f(boxW)}x${f(boxH)} | " +
                    "ratio ancho=${f(if (boxW > 0f) kpW / boxW else 0f)} " +
                    "alto=${f(if (boxH > 0f) kpH / boxH else 0f)} " +
                    "(1.0 = coherente; sobre box PREDICHO → subestima el daño)"
            )
        }

        if (confident == 0) {
            Log.w(
                TAG,
                "Ningún keypoint supera $VISIBLE_CONFIDENCE → el overlay dibuja vacío. Si los " +
                    "valores de confianza son negativos, el export no aplicó sigmoid al canal."
            )
        } else if (inFrame == 0) {
            Log.w(TAG, "Todos los keypoints caen fuera de [0,1] → el esqueleto queda fuera del visor.")
        }
    }

    /**
     * Vuelca a disco la entrada real del modelo (el bitmap ya letterboxeado) para poder
     * inspeccionarla con `adb pull`: descarta de un vistazo un preprocesado roto (imagen
     * verde, corrida o aplastada) como causa de keypoints incoherentes.
     */
    fun dumpInput(context: Context, bitmap: Bitmap) {
        runCatching {
            val file = File(context.getExternalFilesDir(null), "pose_input_dump.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            Log.i(TAG, "Entrada del modelo volcada en ${file.absolutePath}")
        }.onFailure { Log.w(TAG, "No se pudo volcar la entrada del modelo", it) }
    }

    private fun f(value: Float): String = "%.3f".format(value)

    private const val TAG = "PoseDiag"

    /** Techo por encima del cual las coordenadas crudas sólo pueden estar en píxeles. */
    private const val NORMALIZED_MAX = 8f

    /** Mismo umbral con el que `PoseOverlay` decide dibujar una articulación. */
    private const val VISIBLE_CONFIDENCE = 0.25f
}
