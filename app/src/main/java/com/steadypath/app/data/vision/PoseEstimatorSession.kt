package com.steadypath.app.data.vision

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Participación de un ViewModel en el ciclo de vida del [PoseEstimator] compartido.
 *
 * Reemplaza el `launch(Default) { runCatching { warmUp() } }` que cada pantalla con cámara
 * repetía, y garantiza que cada instancia aporte **exactamente una** referencia al
 * estimador: [start] y [stop] son idempotentes, y un [stop] que llega mientras el modelo
 * todavía se está cargando suelta la referencia apenas la carga termina, en vez de dejar
 * el contador desbalanceado.
 *
 * No es singleton: Hilt crea una por ViewModel que la inyecte.
 */
class PoseEstimatorSession @Inject constructor(
    private val estimator: PoseEstimator
) {
    val estado: StateFlow<EstadoEstimador> get() = estimator.estado

    private enum class Fase { INACTIVA, CARGANDO, DETENIDA_DURANTE_CARGA, ACTIVA }

    private val lock = Any()
    private var fase = Fase.INACTIVA

    /** Adquiere el modelo en segundo plano. Llamadas repetidas no suman referencias. */
    fun start(scope: CoroutineScope, dispatcher: CoroutineDispatcher = Dispatchers.Default) {
        synchronized(lock) {
            if (fase != Fase.INACTIVA) return
            fase = Fase.CARGANDO
        }
        scope.launch(dispatcher) {
            val adquirido = runCatching { estimator.acquire() }
                .onFailure { Log.e(TAG, "Fallo al inicializar el estimador de pose", it) }
                .isSuccess
            synchronized(lock) {
                when (fase) {
                    Fase.CARGANDO -> fase = if (adquirido) Fase.ACTIVA else Fase.INACTIVA
                    Fase.DETENIDA_DURANTE_CARGA -> {
                        if (adquirido) estimator.release()
                        fase = Fase.INACTIVA
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Suelta la referencia de esta sesión. Seguro de llamar varias veces o sin [start]. */
    fun stop() {
        synchronized(lock) {
            when (fase) {
                Fase.ACTIVA -> {
                    fase = Fase.INACTIVA
                    estimator.release()
                }
                Fase.CARGANDO -> fase = Fase.DETENIDA_DURANTE_CARGA
                else -> Unit
            }
        }
    }

    private companion object {
        const val TAG = "PoseEstimatorSession"
    }
}
