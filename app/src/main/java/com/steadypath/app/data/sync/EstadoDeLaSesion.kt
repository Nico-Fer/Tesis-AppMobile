package com.steadypath.app.data.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Si el servidor está rechazando el token (401 aun después de intentar renovarlo). Es
 * distinto de no tener red: el servidor respondió, así que esperar no lo arregla y la
 * persona tiene que volver a iniciar sesión. Sin esto veía pantallas vacías sin
 * explicación, porque cada lectura fallaba en silencio.
 *
 * Lo alimenta [com.steadypath.app.data.datasource.SafeApiCaller], por donde pasan las
 * llamadas, y lo muestra el aviso de conexión.
 */
@Singleton
class EstadoDeLaSesion @Inject constructor() {
    private val _vencida = MutableStateFlow(false)
    val vencida: StateFlow<Boolean> = _vencida.asStateFlow()

    fun rechazada() {
        _vencida.value = true
    }

    /** Una respuesta aceptada, un inicio o un cierre de sesión dejan el aviso sin sentido. */
    fun vigente() {
        _vencida.value = false
    }
}
