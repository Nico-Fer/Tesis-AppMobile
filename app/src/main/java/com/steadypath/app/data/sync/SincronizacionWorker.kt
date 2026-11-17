package com.steadypath.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Envía la cola de salida cuando vuelve la red aunque la aplicación esté cerrada. Sin esto
 * lo hecho sin conexión recién salía la próxima vez que la persona abriera la aplicación,
 * y su profesional no lo veía hasta entonces.
 *
 * Toma el [Sincronizador] por un punto de entrada de Hilt en vez de por inyección en el
 * constructor: así no hace falta reemplazar la fábrica de workers de WorkManager.
 */
class SincronizacionWorker(
    context: Context,
    parametros: WorkerParameters
) : CoroutineWorker(context, parametros) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencias {
        fun sincronizador(): Sincronizador
    }

    override suspend fun doWork(): Result {
        val sincronizador = EntryPointAccessors
            .fromApplication(applicationContext, Dependencias::class.java)
            .sincronizador()
        val quedanPendientes = runCatching { sincronizador.enviarPendientes() }.getOrDefault(true)
        return if (quedanPendientes) Result.retry() else Result.success()
    }

    companion object {
        private const val NOMBRE = "sincronizacion-de-pendientes"

        /**
         * APPEND_OR_REPLACE y no KEEP: si justo hay un envío corriendo, la operación que se
         * acaba de encolar puede no estar en la lista que ese envío ya leyó.
         */
        fun programar(context: Context) {
            val pedido = OneTimeWorkRequestBuilder<SincronizacionWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            runCatching {
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(NOMBRE, ExistingWorkPolicy.APPEND_OR_REPLACE, pedido)
            }
        }
    }
}
