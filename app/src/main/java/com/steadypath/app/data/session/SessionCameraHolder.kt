package com.steadypath.app.data.session

import com.steadypath.app.domain.model.CameraLens
import com.steadypath.app.domain.repository.SessionCameraRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lente elegido para el flujo en curso, solo en memoria.
 *
 * Existe porque la elección se hace en la pantalla de detalle del ejercicio pero la cámara
 * se abre varias pantallas después ---preparar espacio, calibración, cuenta regresiva---, y
 * hacer viajar el valor como argumento por cada una de esas rutas ensuciaría cuatro
 * destinos para un dato que ninguno de ellos usa.
 *
 * `null` significa «sin elección puntual»: en ese caso el flujo usa la preferencia
 * persistida del rol. Así el selector previo a la sesión se aparta de la preferencia **sin
 * modificarla**, que es justamente la diferencia entre ajustar el predeterminado y decidir
 * por esta vez.
 *
 * Se limpia al terminar el flujo. Si el proceso muere antes, el siguiente arranque cae a la
 * preferencia, que es el comportamiento correcto.
 */
@Singleton
class SessionCameraHolder @Inject constructor() : SessionCameraRepository {

    private val _lente = MutableStateFlow<CameraLens?>(null)
    override val lente: StateFlow<CameraLens?> = _lente.asStateFlow()

    override fun elegir(lens: CameraLens) {
        _lente.value = lens
    }

    override fun limpiar() {
        _lente.value = null
    }
}
