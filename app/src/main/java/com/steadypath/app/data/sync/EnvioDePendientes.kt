package com.steadypath.app.data.sync

import com.steadypath.app.domain.model.NoAutenticadoException
import com.steadypath.app.domain.model.NotProvisionedException
import com.steadypath.app.domain.model.ServidorNoDisponibleException
import com.steadypath.app.domain.model.SinConexionException

/**
 * Reglas puras de la cola de salida, separadas de Android y de Retrofit para poder
 * probarlas en la JVM.
 */
internal object EnvioDePendientes {

    /**
     * @property quedanPendientes el envío se cortó y quedó algo en la cola.
     * @property sesionVencida el corte fue un 401: el servidor respondió, así que hay red,
     *   pero el token no se pudo renovar. No se arregla esperando: hay que volver a entrar.
     *   (El aviso a la persona sale de [EstadoDeLaSesion], que ve todas las llamadas.)
     */
    data class Resumen(
        val enviadas: Int,
        val descartadas: Int,
        val quedanPendientes: Boolean = false,
        val sesionVencida: Boolean = false
    )

    /**
     * Errores que se resuelven solos con el tiempo (red, servidor caído, token vencido):
     * la operación se guarda y se reintenta. Cualquier otro rechazo del servidor (datos
     * inválidos, rutina ajena) se repetiría igual en cada intento.
     */
    fun esReintentable(error: Throwable): Boolean =
        error is SinConexionException ||
            error is ServidorNoDisponibleException ||
            error is NoAutenticadoException ||
            error is NotProvisionedException

    /**
     * Envía en el orden en que se hicieron las cosas, que no es un detalle: una sesión
     * hecha con una rutina creada sin red solo es válida para el servidor si la rutina
     * llegó antes. Corta en el primer error reintentable (si no hay red para una, tampoco
     * para las siguientes, y saltearla rompería ese orden). Una operación que el servidor
     * rechaza de forma definitiva sale de la cola para no bloquear a las demás;
     * [alDescartar] deshace lo que se había guardado en el teléfono dándola por hecha.
     */
    suspend fun procesar(
        pendientes: List<OperacionPendiente>,
        enviar: suspend (OperacionPendiente) -> Result<*>,
        quitar: suspend (String) -> Unit,
        alDescartar: suspend (OperacionPendiente, Throwable) -> Unit = { _, _ -> }
    ): Resumen {
        var enviadas = 0
        var descartadas = 0
        for (pendiente in pendientes) {
            val error = enviar(pendiente).exceptionOrNull()
            when {
                error == null -> enviadas++
                esReintentable(error) -> return Resumen(
                    enviadas = enviadas,
                    descartadas = descartadas,
                    quedanPendientes = true,
                    sesionVencida = error is NoAutenticadoException
                )
                else -> {
                    descartadas++
                    alDescartar(pendiente, error)
                }
            }
            quitar(pendiente.id)
        }
        return Resumen(enviadas, descartadas)
    }
}
