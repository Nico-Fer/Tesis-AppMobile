package com.steadypath.app.benchmark

import android.content.Context
import android.os.BatteryManager
import android.os.Debug
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * Mediciones de memoria y energía del proceso del benchmark.
 *
 * En Test Lab el equipo está siempre enchufado, así que la batería no se descarga y el
 * consumo no se puede medir. Se usa la **estimación del sistema** (`batterystats`, mAh por
 * UID a partir del tiempo de CPU y el `power_profile` del fabricante). Para que acumule hay
 * que simular el desenchufe con `dumpsys battery unplug`. Mientras dura, `BatteryService`
 * congela el intent de batería (nivel, temperatura, voltaje), así que el desenchufe se
 * limita a la ventana medida: [EnergyWindow] lo abre y lo cierra.
 *
 * Nada de esto se llama dentro del bucle medido, salvo el [Sampler], que lee `/proc` y una
 * propiedad del HAL de batería en su propio hilo.
 */
object ResourceMonitor {

    private const val TAG = "PoseBenchmark"
    private const val KB_PER_MB = 1024.0

    // ---- Memoria ----

    /**
     * PSS del proceso por categoría, en MB (`Debug.getMemoryInfo`: lee smaps, decenas de ms).
     * `graficos` incluye los buffers del delegate de GPU si el equipo expone memtrack.
     */
    fun memorySnapshot(): Map<String, Double?> {
        val info = Debug.MemoryInfo().also(Debug::getMemoryInfo)
        fun stat(name: String) = info.getMemoryStat(name)?.toDoubleOrNull()?.div(KB_PER_MB)
        val status = procStatus()
        return linkedMapOf(
            "pss_mb" to info.totalPss / KB_PER_MB,
            "java_heap_mb" to stat("summary.java-heap"),
            "native_heap_mb" to stat("summary.native-heap"),
            "graficos_mb" to stat("summary.graphics"),
            "rss_mb" to status.rssMb,
            "vmhwm_mb" to status.hwmMb
        )
    }

    class ProcStatus(val rssMb: Double?, val hwmMb: Double?)

    /** `VmRSS` y `VmHWM` (pico de RSS del proceso) de `/proc/self/status`: barato. */
    fun procStatus(): ProcStatus {
        val lines = runCatching { File("/proc/self/status").readLines() }.getOrDefault(emptyList())
        fun kb(key: String) = lines.firstOrNull { it.startsWith("$key:") }
            ?.substringAfter(':')?.trim()?.substringBefore(' ')?.toDoubleOrNull()?.div(KB_PER_MB)
        return ProcStatus(rssMb = kb("VmRSS"), hwmMb = kb("VmHWM"))
    }

    // ---- CPU ----

    private val clockTicksPerSecond: Long by lazy {
        Os.sysconf(OsConstants._SC_CLK_TCK).takeIf { it > 0 } ?: 100L
    }

    /** Tiempo de CPU acumulado del proceso (`utime + stime`, todos los hilos), en ms. */
    fun cpuTimeMs(): Double? = runCatching {
        // El nombre del proceso va entre paréntesis y puede tener espacios: se corta después.
        val fields = File("/proc/self/stat").readText().substringAfterLast(')').trim().split(' ')
        // Tras el ')' el índice 0 es el campo 3 (state): utime = campo 14, stime = campo 15.
        val ticks = fields[11].toLong() + fields[12].toLong()
        ticks * 1000.0 / clockTicksPerSecond
    }.getOrNull()

    // ---- Batería ----

    /** Lecturas que van directo al HAL de batería: no las congela `dumpsys battery unplug`. */
    fun currentNowUa(context: Context): Int? = property(context, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)

    fun chargeCounterUah(context: Context): Int? = property(context, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

    fun levelPct(context: Context): Int? = property(context, BatteryManager.BATTERY_PROPERTY_CAPACITY)

    /** Capacidad total estimada: carga restante / nivel. `null` si el equipo no expone el contador. */
    fun estimatedCapacityMah(context: Context): Double? {
        val counter = chargeCounterUah(context) ?: return null
        val level = levelPct(context)?.takeIf { it > 0 } ?: return null
        return counter / 1000.0 / (level / 100.0)
    }

    private fun property(context: Context, id: Int): Int? {
        val value = context.getSystemService(BatteryManager::class.java).getIntProperty(id)
        // Int.MIN_VALUE = no soportado; 0 en el contador suele significar lo mismo.
        return value.takeIf { it != Int.MIN_VALUE && !(id == BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER && it == 0) }
    }

    // ---- Shell ----

    /** Corre [command] como `shell` (tiene DUMP, BATTERY_STATS y DEVICE_POWER). */
    fun shell(command: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes().decodeToString() }
    }

    /** `u0a245`: el nombre con el que batterystats lista el UID de la app. */
    val uidName: String by lazy {
        val uid = Process.myUid()
        "u${uid / 100_000}a${uid % 100_000 - Process.FIRST_APPLICATION_UID}"
    }

    /**
     * mAh estimados del UID en la sección «Estimated power use». Tolera los formatos de API
     * 30 (`Uid u0a245: 1.23 ( cpu=… )`) y 31+ (`UID u0a245: 1.23 fg: …`).
     */
    fun parseEstimatedMah(dump: String): Double? {
        val section = dump.substringAfter("Estimated power use", missingDelimiterValue = "")
        if (section.isEmpty()) return null
        return Regex("""(?im)^\s*uid\s+${Regex.escape(uidName)}\s*:\s*([\d.]+(?:[eE][-+]?\d+)?)""")
            .find(section)?.groupValues?.get(1)?.toDoubleOrNull()
    }

    /**
     * Ventana de medición de energía: simula el desenchufe, resetea batterystats y, al
     * cerrar, vuelca la estimación del UID y devuelve el estado real del cargador.
     */
    class EnergyWindow private constructor(private val startMs: Long) {

        class Result(val dump: String, val estimatedMah: Double?, val durationMs: Long)

        fun close(): Result {
            val durationMs = SystemClock.elapsedRealtime() - startMs
            val dump = runCatching { shell("dumpsys batterystats ${InstrumentationRegistry.getInstrumentation().targetContext.packageName}") }
                .onFailure { Log.w(TAG, "batterystats falló", it) }
                .getOrDefault("")
            runCatching { shell("dumpsys battery reset") }
            // El intent de batería se re-publica asincrónicamente tras el reset: sin esta
            // espera, `temp_bateria_fin_c` leería el valor congelado al abrir la ventana.
            SystemClock.sleep(STICKY_REFRESH_MS)
            return Result(dump, parseEstimatedMah(dump), durationMs)
        }

        companion object {
            private const val STICKY_REFRESH_MS = 300L

            fun open(): EnergyWindow {
                runCatching {
                    shell("dumpsys battery unplug")
                    shell("dumpsys batterystats --reset")
                }.onFailure { Log.w(TAG, "No se pudo preparar batterystats", it) }
                return EnergyWindow(SystemClock.elapsedRealtime())
            }

            /** Por si un test se cortó con la ventana abierta. */
            fun restoreBattery() {
                runCatching { shell("dumpsys battery reset") }
            }
        }
    }

    /**
     * Muestreo periódico (cada [periodMs]) de RSS, corriente y estado térmico en un hilo
     * daemon, escrito en `muestras.csv`. Lleva el pico de RSS de la fase en curso.
     */
    class Sampler(
        private val context: Context,
        file: File,
        private val periodMs: Long = 500
    ) {
        private val table = CsvTable(file, COLUMNS)
        private val t0 = SystemClock.elapsedRealtime()

        @Volatile private var phase = "inicio"
        @Volatile private var lap = 0
        @Volatile private var phaseMaxRss: Double? = null
        @Volatile private var running = true

        private val thread = Thread({
            while (running) {
                runCatching { sample() }.onFailure { Log.w(TAG, "Muestra fallida", it) }
                try {
                    Thread.sleep(periodMs)
                } catch (_: InterruptedException) {
                    break
                }
            }
        }, "BenchmarkSampler").apply { isDaemon = true; start() }

        fun beginPhase(name: String, vuelta: Int) {
            phaseMaxRss = null
            lap = vuelta
            phase = name
            runCatching { sample() }
        }

        /** Cierra la fase y devuelve su pico de RSS (MB). */
        fun endPhase(): Double? {
            runCatching { sample() }
            phase = "entre_fases"
            return phaseMaxRss
        }

        fun stop() {
            running = false
            thread.interrupt()
        }

        @Synchronized
        private fun sample() {
            val status = procStatus()
            status.rssMb?.let { rss -> phaseMaxRss = maxOf(phaseMaxRss ?: rss, rss) }
            table.append(
                linkedMapOf(
                    "t_ms" to SystemClock.elapsedRealtime() - t0,
                    "vuelta" to lap,
                    "fase" to phase,
                    "rss_mb" to status.rssMb,
                    "vmhwm_mb" to status.hwmMb,
                    "corriente_ua" to currentNowUa(context),
                    "contador_carga_uah" to chargeCounterUah(context),
                    "nivel_pct" to levelPct(context),
                    "estado_termico" to DeviceInfo.thermalStatus(context)
                )
            )
        }

        companion object {
            val COLUMNS = listOf(
                "t_ms", "vuelta", "fase", "rss_mb", "vmhwm_mb", "corriente_ua",
                "contador_carga_uah", "nivel_pct", "estado_termico"
            )
        }
    }
}
