package com.steadypath.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.steadypath.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Si el teléfono tiene una red con salida a internet. Es una pista, no una garantía: que
 * haya red no dice que el backend responda, así que nadie debe tratarlo como la verdad
 * sobre una llamada. Sirve para tres cosas: no esperar el vencimiento de una llamada que se
 * sabe perdida, avisarle a la persona que está viendo datos guardados y disparar el envío
 * de la cola de salida cuando la red vuelve.
 */
@Singleton
class MonitorDeConectividad @Inject constructor(
    @ApplicationContext context: Context
) {
    private val conectividad = context.getSystemService(ConnectivityManager::class.java)

    private val _enLinea = MutableStateFlow(redActualSirve())
    val enLinea: StateFlow<Boolean> = _enLinea.asStateFlow()

    init {
        runCatching {
            conectividad?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, capacidades: NetworkCapabilities) {
                    _enLinea.value = sirve(capacidades)
                }

                override fun onLost(network: Network) {
                    _enLinea.value = false
                }
            })
        }.onFailure { Log.w(TAG, "No se pudo registrar el aviso de conectividad", it) }
    }

    fun hayRed(): Boolean = _enLinea.value

    private fun redActualSirve(): Boolean {
        // Sin el servicio no hay forma de saberlo: mejor intentar la llamada que darla por perdida.
        val servicio = conectividad ?: return true
        val capacidades = runCatching {
            servicio.getNetworkCapabilities(servicio.activeNetwork)
        }.getOrNull()
        return capacidades != null && sirve(capacidades)
    }

    private fun sirve(capacidades: NetworkCapabilities): Boolean =
        capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            (!EXIGE_INTERNET_VALIDADA ||
                capacidades.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))

    private companion object {
        const val TAG = "MonitorConectividad"

        /**
         * Contra AWS una red sin salida real a internet (portal cautivo, Wi-Fi sin
         * servicio) no sirve. Contra el backend local sí puede servir: el servidor está en
         * la misma red y el teléfono llega aunque Android no la dé por validada.
         */
        val EXIGE_INTERNET_VALIDADA = BuildConfig.FLAVOR != "local"
    }
}
