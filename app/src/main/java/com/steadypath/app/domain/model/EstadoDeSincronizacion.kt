package com.steadypath.app.domain.model

/**
 * Lo que la persona necesita saber sobre el modo sin conexión: si está viendo datos
 * guardados y si hay cosas suyas que todavía no llegaron al servidor.
 *
 * @property pendientes operaciones hechas sin red que esperan en el teléfono.
 * @property rechazadas operaciones que el servidor no aceptó al enviarlas y se perdieron.
 * @property sesionVencida hay red pero la sesión no se puede renovar: lo pendiente no sale
 *   hasta que la persona vuelva a iniciar sesión.
 */
data class EstadoDeSincronizacion(
    val enLinea: Boolean = true,
    val pendientes: Int = 0,
    val rechazadas: Int = 0,
    val sesionVencida: Boolean = false
) {
    val hayAlgoQueAvisar: Boolean
        get() = !enLinea || pendientes > 0 || rechazadas > 0 || sesionVencida
}
