package com.steadypath.app.benchmark

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.io.File

/** Identificación del dispositivo y su estado térmico, para rotular cada fila de resultados. */
object DeviceInfo {

    /** Pares clave-valor estables durante la corrida (van a `dispositivo.csv`). */
    fun describe(context: Context): LinkedHashMap<String, String> {
        val memory = ActivityManager.MemoryInfo().also {
            context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)
        }
        return linkedMapOf(
            "fabricante" to Build.MANUFACTURER,
            "modelo" to Build.MODEL,
            // Coincide con el MODEL_ID del catálogo de Test Lab (p. ej. r8q, redfin).
            "codename" to Build.DEVICE,
            "soc" to soc(),
            "gpu" to (gpuRenderer() ?: "desconocida"),
            "android" to Build.VERSION.RELEASE,
            "api" to Build.VERSION.SDK_INT.toString(),
            "abi" to Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            "nucleos" to Runtime.getRuntime().availableProcessors().toString(),
            "cpu_max_mhz" to maxCpuMhz()?.toString().orEmpty(),
            "ram_mb" to (memory.totalMem / (1024 * 1024)).toString(),
            "fingerprint" to Build.FINGERPRINT
        )
    }

    /** `SM7125 (QTI)` en API 31+; antes de eso el hardware/board es lo único disponible. */
    fun soc(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL != Build.UNKNOWN) {
            "${Build.SOC_MODEL} (${Build.SOC_MANUFACTURER})"
        } else {
            "${Build.HARDWARE}/${Build.BOARD}"
        }

    /** Temperatura de batería en °C — proxy grueso del calentamiento entre clips. */
    fun batteryTempC(context: Context): Double? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tenths = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return tenths?.takeIf { it != Int.MIN_VALUE }?.div(10.0)
    }

    /**
     * Cargador conectado según el intent de batería (`ac`, `usb`, `inalambrico`, `dock` o
     * `ninguno`). Hay que leerlo **fuera** de una `EnergyWindow`: con `dumpsys battery unplug`
     * el intent dice `ninguno` aunque el equipo siga cargando.
     */
    fun charger(context: Context): String {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return when (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "inalambrico"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "dock"
            0 -> "ninguno"
            else -> "desconocido"
        }
    }

    /** `PowerManager.THERMAL_STATUS_*` (0 = sin throttling … 6 = apagado inminente). */
    fun thermalStatus(context: Context): Int =
        context.getSystemService(PowerManager::class.java).currentThermalStatus

    private fun maxCpuMhz(): Int? = (0 until Runtime.getRuntime().availableProcessors())
        .mapNotNull { cpu ->
            runCatching {
                File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toInt()
            }.getOrNull()
        }
        .maxOrNull()
        ?.div(1000)

    /**
     * `GL_RENDERER` (p. ej. "Adreno (TM) 618") con un contexto EGL descartable. No se llama
     * a `eglTerminate`: el display por defecto es único por proceso y el delegate de GPU
     * puede estar usándolo (backend OpenGL).
     */
    private fun gpuRenderer(): String? = runCatching {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        check(EGL14.eglInitialize(display, version, 0, version, 1))
        run {
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            val attribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            check(EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) && count[0] > 0)
            val context = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            val surface = EGL14.eglCreatePbufferSurface(
                display, configs[0], intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
            )
            try {
                check(EGL14.eglMakeCurrent(display, surface, surface, context))
                GLES20.glGetString(GLES20.GL_RENDERER)
            } finally {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(display, surface)
                EGL14.eglDestroyContext(display, context)
            }
        }
    }.getOrNull()
}
