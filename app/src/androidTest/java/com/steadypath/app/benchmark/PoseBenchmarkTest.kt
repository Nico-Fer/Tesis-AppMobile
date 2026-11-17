package com.steadypath.app.benchmark

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.steadypath.app.BuildConfig
import com.steadypath.app.data.comparison.DtwMovementComparator
import com.steadypath.app.data.comparison.LocalAssetReferenceRepository
import com.steadypath.app.data.vision.PoseFrameTimings
import com.steadypath.app.data.vision.PoseModelSpec
import com.steadypath.app.data.vision.YoloPoseEstimator
import com.steadypath.app.domain.comparison.DefaultAngleExtractor
import com.steadypath.app.domain.comparison.PeakValleyRepetitionSegmenter
import com.steadypath.app.domain.comparison.RepMetricsSink
import com.steadypath.app.domain.model.KeypointType
import com.steadypath.app.domain.model.PatientTolerance
import com.steadypath.app.domain.model.PoseFrame
import com.steadypath.app.domain.repository.PatientToleranceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

/**
 * Benchmark de rendimiento del pipeline de pose sobre clips del dataset, pensado para
 * correr en Firebase Test Lab (ver `docs/benchmark-firebase-test-lab.md`).
 *
 * Cada clip se reproduce **como si fuera la cámara**: los frames entran por el mismo
 * `estimate(ImageProxy, rotationDegrees)` que usa el analyzer de CameraX, y un reloj
 * virtual reproduce `STRATEGY_KEEP_ONLY_LATEST` — mientras el pipeline está ocupado los
 * frames que llegan se descartan y se procesa el último disponible. Así los fps efectivos
 * y la cadencia que ve el comparador son los que tendría un paciente con ese dispositivo.
 *
 * Por clip quedan: tiempos por etapa de cada frame procesado, la pose cruda (para comparar
 * offline contra la línea de base de escritorio y el GT de UCO) y, si el clip tiene
 * ejercicio con referencia, las métricas DTW por repetición — mismo formato que el
 * `rep-metrics.csv` de las sesiones reales.
 *
 * Argumentos de instrumentación opcionales (`-e clave valor`, o
 * `-Pandroid.testInstrumentationRunnerArguments.clave=valor` desde Gradle):
 * - `benchmarkDelegate`: `gpu` (default, igual que producción) o `cpu`.
 * - `benchmarkClips`: ids separados por coma (default: todos los del catálogo).
 * - `benchmarkVueltas`: pasadas completas sobre los clips, para medir el throttling
 *   térmico de una sesión larga (default 1).
 */
@RunWith(Parameterized::class)
class PoseBenchmarkTest(
    private val vuelta: Int,
    private val clip: ClipManifest.Clip,
    @Suppress("unused") private val nombre: String
) {

    @Test
    fun benchmark() {
        val tempStart = DeviceInfo.batteryTempC(targetContext)
        val clock = KeepOnlyLatestClock()
        val processed = ArrayList<ProcessedFrame>()
        var decoderName = ""

        val resources = measureResources(clip.id, vuelta, "${clip.id}_v$vuelta") {
            VideoFrameDecoder(testContext.assets.openFd("${ClipManifest.ASSET_DIR}/${clip.archivo}")).use { decoder ->
                val pts = decoder.presentationTimesUs()
                decoderName = decoder.codecName
                decoder.forEachFrame { index, ptsUs, image ->
                    val nextMs = pts.getOrNull(index + 1)?.div(1000.0)
                    if (!clock.shouldProcess(nextMs)) return@forEachFrame

                    lastTimings = null
                    val startNs = System.nanoTime()
                    val pose = estimator.estimate(MediaImageProxy(image, clip.rotationDegrees), clip.rotationDegrees)
                    val wallMs = (System.nanoTime() - startNs) / 1_000_000.0
                    val timings = checkNotNull(lastTimings) {
                        "El estimador descartó el frame $index sin inferir: el modelo no está cargado"
                    }
                    val ptsMs = ptsUs / 1000.0
                    processed += ProcessedFrame(
                        index = index,
                        ptsMs = ptsMs,
                        virtualStartMs = clock.onProcessed(ptsMs, wallMs),
                        wallMs = wallMs,
                        timings = timings,
                        // Marca de captura del frame, no la del reloj de pared: el segmentador
                        // usa tiempos y tiene que ver la cadencia real del video.
                        pose = pose?.copy(timestampMs = ptsMs.toLong()),
                        warmup = framesSinceAcquire++ < WARMUP_FRAMES
                    )
                }
            }
        }

        val reps = compareRepetitions(processed)
        val summary = summarize(processed, reps, tempStart, decoderName, resources)
        writeRows(processed, reps?.rows.orEmpty(), summary)

        // Las aserciones van después de escribir: un dispositivo que falla igual deja datos.
        assertTrue("No se procesó ningún frame de ${clip.id}", processed.isNotEmpty())
        val detection = summary.getValue("deteccion") as Double
        if (manifest.modeloEscritorio == PoseModelSpec.ACTIVE.assetFileName) {
            assertTrue(
                "Detección en ${clip.id} = ${"%.2f".format(detection)} muy por debajo de la de " +
                    "escritorio (${clip.deteccionEscritorio}) con el mismo modelo: revisar el " +
                    "delegate de este dispositivo",
                detection >= clip.deteccionEscritorio - DETECTION_TOLERANCE
            )
        } else {
            Log.w(TAG, "Línea de base de ${manifest.modeloEscritorio}, modelo activo " +
                "${PoseModelSpec.ACTIVE.assetFileName}: se omite la comparación de detección")
        }
    }

    /**
     * Corre el comparador DTW real sobre las poses detectadas, en el orden en que llegaron
     * (el ViewModel tampoco le pasa los frames sin persona). `null` si el clip no tiene
     * ejercicio asociado.
     */
    private fun compareRepetitions(processed: List<ProcessedFrame>): RepOutcome? {
        val exerciseId = clip.exerciseId ?: return null
        val rows = ArrayList<Map<String, Any?>>()
        // Valores sin redondear (toString), igual que CsvRepMetricsSink: estas filas sirven
        // para calibrar umbrales.
        val sink = RepMetricsSink { id, repIndex, distance, dtwSimilarity, shapeScore, finalScore ->
            rows += linkedMapOf(
                "dispositivo" to Build.MODEL, "vuelta" to vuelta, "clip" to clip.id,
                "timestampMs" to System.currentTimeMillis(), "exerciseId" to id,
                "repIndex" to repIndex, "normalizedDistance" to distance.toString(),
                "dtwSimilarity" to dtwSimilarity.toString(), "shapeScore" to shapeScore.toString(),
                "finalScore" to finalScore
            )
        }
        val comparator = DtwMovementComparator(
            referenceRepository = LocalAssetReferenceRepository(targetContext, Dispatchers.IO),
            angleExtractor = DefaultAngleExtractor(),
            segmenterFactory = { driver, amplitude ->
                PeakValleyRepetitionSegmenter.tunedFor(driver, amplitude)
            },
            toleranceRepository = NoTolerance,
            computeDispatcher = Dispatchers.Default,
            log = { Log.i(TAG, it) },
            metricsSink = sink
        )
        val results = runBlocking {
            comparator.compare(exerciseId, processed.mapNotNull { it.pose }.asFlow()).toList()
        }
        return RepOutcome(rows = rows, detected = results.sumOf { it.repDelta })
    }

    /** [rows] = una fila por rep puntuada; [detected] incluye la primera, que no se puntúa. */
    private class RepOutcome(val rows: List<Map<String, Any?>>, val detected: Int)

    private fun summarize(
        processed: List<ProcessedFrame>,
        reps: RepOutcome?,
        tempStart: Double?,
        decoderName: String,
        resources: ClipResources
    ): Map<String, Any?> {
        val steady = processed.filterNot { it.warmup }.ifEmpty { processed }
        val inference = steady.map { it.timings.inferenceMs }
        val total = steady.map { it.wallMs }
        val durationS = clip.frames / clip.fps
        val detected = processed.mapNotNull { it.pose }
        val inferenceP50 = percentile(inference, 50.0)
        val loopMin = resources.wallMs / 60_000.0
        return linkedMapOf(
            "dispositivo" to Build.MODEL,
            "codename" to Build.DEVICE,
            "soc" to DeviceInfo.soc(),
            "android" to Build.VERSION.RELEASE,
            "modelo" to PoseModelSpec.ACTIVE.assetFileName,
            "delegate" to estimator.backend?.name,
            "vuelta" to vuelta,
            "clip" to clip.id,
            "grupo" to clip.grupo,
            "frames_video" to clip.frames,
            "duracion_s" to durationS,
            "frames_procesados" to processed.size,
            "fps_efectivo" to processed.size / durationS,
            "inferencia_media_ms" to inference.averageOrNull(),
            "inferencia_p50_ms" to inferenceP50,
            "inferencia_p90_ms" to percentile(inference, 90.0),
            "inferencia_min_ms" to inference.minOrNull(),
            "inferencia_max_ms" to inference.maxOrNull(),
            "fps_inferencia" to inferenceP50?.let { 1000.0 / it },
            "preproceso_p50_ms" to percentile(steady.map { it.timings.preprocessMs }, 50.0),
            "postproceso_p50_ms" to percentile(steady.map { it.timings.postprocessMs }, 50.0),
            "total_p50_ms" to percentile(total, 50.0),
            "total_p90_ms" to percentile(total, 90.0),
            "deteccion" to detected.size.toDouble() / processed.size.coerceAtLeast(1),
            "deteccion_escritorio" to clip.deteccionEscritorio,
            "conf_kp_media" to detected.flatMap { p -> p.keypoints.map { it.confidence.toDouble() } }
                .averageOrNull(),
            "reps_esperadas" to clip.repsEsperadas,
            "reps_detectadas" to reps?.detected,
            "reps_puntuadas" to reps?.rows?.size,
            "score_medio" to reps?.rows?.map { (it["finalScore"] as Float).toDouble() }
                ?.filter { it.isFinite() }?.averageOrNull(),
            "temp_bateria_inicio_c" to tempStart,
            "temp_bateria_fin_c" to DeviceInfo.batteryTempC(targetContext),
            "estado_termico_fin" to DeviceInfo.thermalStatus(targetContext),
            "decoder" to decoderName,
            // Recursos: columnas nuevas al final para no romper a quien lee las anteriores.
            "duracion_bucle_s" to resources.wallMs / 1000.0,
            "ocupacion_pct" to 100.0 * processed.sumOf { it.wallMs } / resources.wallMs.coerceAtLeast(1),
            "cpu_ms" to resources.cpuMs,
            "cpu_ms_por_frame" to resources.cpuMs?.div(processed.size.coerceAtLeast(1)),
            "uso_cpu_pct" to resources.cpuMs?.let { 100.0 * it / resources.wallMs.coerceAtLeast(1) },
            // Como texto: CsvTable redondea los Double a 3 decimales y en reposo o en un
            // clip corto el consumo cae por debajo de 0.001 mAh.
            "consumo_estimado_mah" to resources.energy.estimatedMah?.toString(),
            "consumo_mah_por_min" to resources.energy.estimatedMah?.takeIf { loopMin > 0 }?.div(loopMin)?.toString(),
            "pss_fin_mb" to resources.memory["pss_mb"],
            "java_heap_fin_mb" to resources.memory["java_heap_mb"],
            "native_heap_fin_mb" to resources.memory["native_heap_mb"],
            "graficos_fin_mb" to resources.memory["graficos_mb"],
            "rss_max_mb" to resources.rssMaxMb,
            "vmhwm_mb" to resources.memory["vmhwm_mb"]
        )
    }

    private fun writeRows(
        processed: List<ProcessedFrame>,
        reps: List<Map<String, Any?>>,
        summary: Map<String, Any?>
    ) {
        for (f in processed) {
            framesTable.append(
                linkedMapOf(
                    "vuelta" to vuelta, "clip" to clip.id, "frame" to f.index, "pts_ms" to f.ptsMs,
                    "inicio_virtual_ms" to f.virtualStartMs,
                    "preproceso_ms" to f.timings.preprocessMs,
                    "inferencia_ms" to f.timings.inferenceMs,
                    "postproceso_ms" to f.timings.postprocessMs,
                    "total_ms" to f.wallMs, "warmup" to if (f.warmup) 1 else 0,
                    "detectado" to if (f.pose != null) 1 else 0, "score" to f.pose?.score
                )
            )
            val pose = f.pose ?: continue
            val row = linkedMapOf<String, Any?>("vuelta" to vuelta, "clip" to clip.id, "frame" to f.index)
            for (kp in pose.keypoints) {
                val name = kp.type.name.lowercase()
                row["${name}_x"] = kp.x
                row["${name}_y"] = kp.y
                row["${name}_c"] = kp.confidence
            }
            posesTable.append(row)
        }
        reps.forEach(repsTable::append)
        summaryTable.append(summary)
        Log.i(TAG, "Clip ${clip.id} (vuelta $vuelta): $summary")
    }

    /** Lo que consumió una fase (un clip o el reposo), medido alrededor del bucle. */
    private class ClipResources(
        val wallMs: Long,
        val cpuMs: Double?,
        val rssMaxMb: Double?,
        val energy: ResourceMonitor.EnergyWindow.Result,
        val memory: Map<String, Double?>
    )

    private class ProcessedFrame(
        val index: Int,
        val ptsMs: Double,
        val virtualStartMs: Double,
        val wallMs: Double,
        val timings: PoseFrameTimings,
        val pose: PoseFrame?,
        val warmup: Boolean
    )

    /**
     * `STRATEGY_KEEP_ONLY_LATEST` sobre un stream con timestamps conocidos: el analyzer
     * recibe un frame si, cuando se libera, todavía no llegó el siguiente; si ya llegó,
     * ese frame se descartó a favor del más nuevo.
     */
    private class KeepOnlyLatestClock {
        private var busyUntilMs = Double.NEGATIVE_INFINITY

        fun shouldProcess(nextPtsMs: Double?): Boolean = nextPtsMs == null || nextPtsMs > busyUntilMs

        /** Registra el procesamiento y devuelve el instante virtual en que arrancó. */
        fun onProcessed(ptsMs: Double, durationMs: Double): Double {
            val start = max(busyUntilMs, ptsMs)
            busyUntilMs = start + durationMs
            return start
        }
    }

    /** Sin calibración propia: el comparador usa la amplitud de la referencia empaquetada. */
    private object NoTolerance : PatientToleranceRepository {
        override suspend fun getOwnTolerance(idEjercicio: String): Result<PatientTolerance> =
            Result.failure(NoSuchElementException("benchmark sin paciente"))
    }

    companion object {
        private const val TAG = "PoseBenchmark"
        private const val ARG_DELEGATE = "benchmarkDelegate"
        private const val ARG_CLIPS = "benchmarkClips"
        private const val ARG_VUELTAS = "benchmarkVueltas"
        private const val ARG_REPOSO = "benchmarkReposoS"

        /** Segundos con el modelo cargado y sin inferir: línea de base de CPU y consumo. */
        private const val DEFAULT_REPOSO_S = 20L

        /** Los primeros frames tras crear el intérprete incluyen compilación de kernels. */
        private const val WARMUP_FRAMES = 3

        /** Cuánto menos que escritorio puede detectar un dispositivo antes de fallar. */
        private const val DETECTION_TOLERANCE = 0.15

        private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
        private val targetContext: Context get() = instrumentation.targetContext
        private val testContext: Context get() = instrumentation.context
        private val args get() = InstrumentationRegistry.getArguments()

        private val manifest: ClipManifest by lazy { ClipManifest.load(testContext.assets) }

        private lateinit var estimator: YoloPoseEstimator
        private lateinit var runDir: File
        private lateinit var framesTable: CsvTable
        private lateinit var posesTable: CsvTable
        private lateinit var repsTable: CsvTable
        private lateinit var summaryTable: CsvTable
        private var sampler: ResourceMonitor.Sampler? = null

        /** Lo escribe el sink del estimador; todo corre en el hilo del test. */
        private var lastTimings: PoseFrameTimings? = null
        private var framesSinceAcquire = 0

        @JvmStatic
        @Parameterized.Parameters(name = "{2}_v{0}")
        fun parameters(): List<Array<Any>> {
            val wanted = args.getString(ARG_CLIPS)
                ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.toSet()
            val clips = manifest.clips.filter { wanted == null || it.id in wanted }
            require(clips.isNotEmpty()) { "Ningún clip del catálogo coincide con $ARG_CLIPS=$wanted" }
            val vueltas = args.getString(ARG_VUELTAS)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            return (1..vueltas).flatMap { v -> clips.map { arrayOf<Any>(v, it, it.id) } }
        }

        @JvmStatic
        @BeforeClass
        fun openSession() {
            // Antes de crear el intérprete: la consulta de GPU abre su propio contexto EGL.
            val device = DeviceInfo.describe(targetContext)
            // Después de describe (que ya cargó el driver de GPU) y antes del intérprete: la
            // diferencia es lo que cuesta el modelo con su delegate.
            val memoryBefore = ResourceMonitor.memorySnapshot()
            val charger = DeviceInfo.charger(targetContext)
            val requested = args.getString(ARG_DELEGATE, "gpu").lowercase()
            estimator = YoloPoseEstimator(
                context = targetContext,
                spec = PoseModelSpec.ACTIVE,
                useGpu = requested != "cpu"
            ) { lastTimings = it }
            val startNs = System.nanoTime()
            estimator.acquire()
            val initMs = (System.nanoTime() - startNs) / 1_000_000.0

            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            runDir = File(outputRoot(), "${stamp}_${Build.DEVICE}_${estimator.backend}")
            val memoryLoaded = ResourceMonitor.memorySnapshot()
            sampler = ResourceMonitor.Sampler(targetContext, File(runDir, "muestras.csv"))
            val idle = idleBaseline()
            CsvTable(File(runDir, "dispositivo.csv"), listOf("clave", "valor")).apply {
                val extra = linkedMapOf(
                    "modelo_tflite" to PoseModelSpec.ACTIVE.assetFileName,
                    "delegate_pedido" to requested,
                    "delegate_efectivo" to estimator.backend?.name.orEmpty(),
                    "init_modelo_ms" to "%.1f".format(Locale.US, initMs),
                    "build_type" to BuildConfig.BUILD_TYPE,
                    "fecha" to stamp,
                    "cargador_inicio" to charger,
                    // Para re-parsear batterystats/*.txt offline (agregar_resultados.py).
                    "uid_batterystats" to ResourceMonitor.uidName,
                    "bateria_capacidad_estimada_mah" to ResourceMonitor.estimatedCapacityMah(targetContext)?.let { "%.0f".format(Locale.US, it) }.orEmpty()
                ) + memoryBefore.mbEntries("antes_modelo") + memoryLoaded.mbEntries("con_modelo") + idle
                (device + extra).forEach { (k, v) -> append(linkedMapOf("clave" to k, "valor" to v)) }
            }
            framesTable = CsvTable(
                File(runDir, "frames.csv"),
                listOf(
                    "vuelta", "clip", "frame", "pts_ms", "inicio_virtual_ms", "preproceso_ms",
                    "inferencia_ms", "postproceso_ms", "total_ms", "warmup", "detectado", "score"
                )
            )
            posesTable = CsvTable(
                File(runDir, "poses.csv"),
                listOf("vuelta", "clip", "frame") + KeypointType.entries.flatMap { type ->
                    val n = type.name.lowercase()
                    listOf("${n}_x", "${n}_y", "${n}_c")
                }
            )
            repsTable = CsvTable(
                File(runDir, "reps.csv"),
                listOf(
                    "dispositivo", "vuelta", "clip", "timestampMs", "exerciseId", "repIndex",
                    "normalizedDistance", "dtwSimilarity", "shapeScore", "finalScore"
                )
            )
            summaryTable = SummaryColumns.table(File(runDir, "resumen.csv"))
            Log.i(TAG, "Resultados en ${runDir.absolutePath} (init ${"%.0f".format(initMs)} ms, ${estimator.backend})")
        }

        @JvmStatic
        @AfterClass
        fun closeSession() {
            // Con el modelo todavía cargado: es el estado que interesa citar.
            if (::runDir.isInitialized) {
                runCatching { File(runDir, "meminfo_fin.txt").writeText(ResourceMonitor.shell("dumpsys meminfo ${targetContext.packageName}")) }
                    .onFailure { Log.w(TAG, "No se pudo volcar meminfo", it) }
            }
            sampler?.stop()
            sampler = null
            ResourceMonitor.EnergyWindow.restoreBattery()
            if (::estimator.isInitialized) estimator.release()
            // Corrida local de AGP: se copia además a la carpeta que AGP baja sola a
            // build/outputs/connected_android_test_additional_output.
            val agpDir = args.getString("additionalTestOutputDir")?.let(::File) ?: return
            if (::runDir.isInitialized) {
                runCatching { runDir.copyRecursively(File(agpDir, "benchmark/${runDir.name}"), overwrite = true) }
                    .onFailure { Log.w(TAG, "No se pudo copiar a $agpDir", it) }
            }
        }

        /**
         * Mide CPU, pico de RSS, memoria final y consumo estimado alrededor de [block]. El
         * volcado de batterystats queda en `batterystats/<archivo>.txt` para auditar el parseo.
         * Las mediciones van fuera del bucle de frames, salvo el muestreador en su hilo.
         */
        private fun measureResources(phase: String, lap: Int, dumpName: String, block: () -> Unit): ClipResources {
            val window = ResourceMonitor.EnergyWindow.open()
            sampler?.beginPhase(phase, lap)
            val cpuStart = ResourceMonitor.cpuTimeMs()
            var cpuEnd: Double? = null
            var rssMax: Double? = null
            var energy: ResourceMonitor.EnergyWindow.Result? = null
            try {
                block()
            } finally {
                cpuEnd = ResourceMonitor.cpuTimeMs()
                rssMax = sampler?.endPhase()
                energy = window.close()
            }
            val result = checkNotNull(energy)
            runCatching {
                File(runDir, "batterystats/$dumpName.txt").apply { parentFile?.mkdirs() }.writeText(result.dump)
            }
            return ClipResources(
                wallMs = result.durationMs,
                cpuMs = if (cpuStart != null && cpuEnd != null) cpuEnd - cpuStart else null,
                rssMaxMb = rssMax,
                energy = result,
                memory = ResourceMonitor.memorySnapshot()
            )
        }

        /** Reposo con el modelo cargado: lo que consume la app sin inferir. */
        private fun idleBaseline(): Map<String, String> {
            val seconds = args.getString(ARG_REPOSO)?.toLongOrNull()?.coerceAtLeast(0) ?: DEFAULT_REPOSO_S
            if (seconds == 0L) return emptyMap()
            val idle = measureResources("reposo", 0, "reposo") { Thread.sleep(seconds * 1000) }
            val minutes = idle.wallMs / 60_000.0
            fun fmt(v: Double?) = v?.takeIf { it.isFinite() }?.toString().orEmpty()
            return linkedMapOf(
                "reposo_s" to "%.1f".format(Locale.US, idle.wallMs / 1000.0),
                "reposo_cpu_pct" to fmt(idle.cpuMs?.let { 100.0 * it / idle.wallMs.coerceAtLeast(1) }),
                "reposo_mah" to fmt(idle.energy.estimatedMah),
                "reposo_mah_por_min" to fmt(idle.energy.estimatedMah?.div(minutes)),
                "reposo_rss_max_mb" to fmt(idle.rssMaxMb)
            )
        }

        /** `pss_<sufijo>_mb`, `java_heap_<sufijo>_mb`, … para `dispositivo.csv`. */
        private fun Map<String, Double?>.mbEntries(suffix: String): Map<String, String> =
            entries.associate { (key, value) ->
                key.removeSuffix("_mb") + "_${suffix}_mb" to value?.let { "%.1f".format(Locale.US, it) }.orEmpty()
            }

        /**
         * `Android/media/<pkg>/benchmark`: escribible sin permisos y legible por el shell,
         * que es lo que necesita el `directoriesToPull` de Test Lab — es el mismo directorio
         * que elige androidx.benchmark para dejar sus resultados en API 30+.
         */
        @Suppress("DEPRECATION")
        private fun outputRoot(): File = File(targetContext.externalMediaDirs.first(), "benchmark")
    }
}

/** Columnas de `resumen.csv`, en el orden de [PoseBenchmarkTest.summarize]. */
private object SummaryColumns {
    val names = listOf(
        "dispositivo", "codename", "soc", "android", "modelo", "delegate", "vuelta", "clip",
        "grupo", "frames_video", "duracion_s", "frames_procesados", "fps_efectivo",
        "inferencia_media_ms", "inferencia_p50_ms", "inferencia_p90_ms", "inferencia_min_ms",
        "inferencia_max_ms", "fps_inferencia", "preproceso_p50_ms", "postproceso_p50_ms",
        "total_p50_ms", "total_p90_ms", "deteccion", "deteccion_escritorio", "conf_kp_media",
        "reps_esperadas", "reps_detectadas", "reps_puntuadas", "score_medio",
        "temp_bateria_inicio_c", "temp_bateria_fin_c", "estado_termico_fin", "decoder",
        "duracion_bucle_s", "ocupacion_pct", "cpu_ms", "cpu_ms_por_frame", "uso_cpu_pct",
        "consumo_estimado_mah", "consumo_mah_por_min", "pss_fin_mb", "java_heap_fin_mb",
        "native_heap_fin_mb", "graficos_fin_mb", "rss_max_mb", "vmhwm_mb"
    )

    fun table(file: File) = CsvTable(file, names)
}

/** Percentil por interpolación lineal; `null` si no hay muestras. */
private fun percentile(values: List<Double>, p: Double): Double? {
    if (values.isEmpty()) return null
    val sorted = values.sorted()
    val rank = p / 100.0 * (sorted.size - 1)
    val low = rank.toInt()
    val high = minOf(low + 1, sorted.size - 1)
    return sorted[low] + (sorted[high] - sorted[low]) * (rank - low)
}

private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()
