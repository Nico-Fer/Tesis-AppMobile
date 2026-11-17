package com.steadypath.app.presentation.screens.calibration

import android.util.Log

/**
 * Log de diagnóstico de la checklist de encuadre (`PrepareSpaceScreen`), pensado para
 * responder desde logcat **por qué un ítem no cambia**: a diferencia de [PoseDiagnostics][
 * com.steadypath.app.data.vision.PoseDiagnostics] (que diagnostica el modelo de pose), esto
 * imprime los números crudos de luz y de movimiento, no solo el booleano ya evaluado — si un
 * umbral está mal calibrado para uso real (sostenido con la mano, no en un trípode), acá se ve
 * el valor real contra el que compara.
 *
 * Mismo patrón que `PoseDiagnostics`: llamado cada N eventos desde quien ya tiene la medición
 * a mano, gateado en `BuildConfig.DEBUG` por el caller.
 */
internal object FramingDiagnostics {

    fun log(
        lumaPromedio: Float,
        umbralLuz: Float,
        magnitudSuavizada: Float,
        umbralMovimiento: Float,
        cuerpoVisible: Boolean,
        buenaIluminacion: Boolean,
        camaraEstable: Boolean
    ) {
        Log.d(
            TAG,
            "luz=${f(lumaPromedio)}/${f(umbralLuz)} " +
                "(${if (buenaIluminacion) "OK" else "BAJA"}) | " +
                "movimiento=${f(magnitudSuavizada)}/${f(umbralMovimiento)} " +
                "(${if (camaraEstable) "ESTABLE" else "INESTABLE"}) | " +
                "cuerpo=${if (cuerpoVisible) "visible" else "no visible"} | " +
                "listo=${cuerpoVisible && buenaIluminacion && camaraEstable}"
        )
    }

    private fun f(value: Float): String = "%.2f".format(value)

    private const val TAG = "FramingDiag"
}
