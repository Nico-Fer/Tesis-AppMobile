package com.steadypath.app.domain.model

/**
 * Lo que el sistema guarda sobre la persona, para que lo vea sin pedirlo por escrito
 * (derecho de acceso, Ley 25.326 art. 14). Las cantidades que no aplican a su rol valen 0.
 */
data class MisDatos(
    val nombre: String,
    val email: String,
    val rol: String,
    val telefono: String?,
    val edad: Int?,
    val matricula: String?,
    val institucion: String?,
    /** Fechas en formato ISO, tal como las devuelve el backend. */
    val fechaRegistro: String,
    val ultimaActividad: String,
    val bajaPorInactividadDesde: String,
    val profesionalAsignado: String?,
    val sesiones: Int,
    val registrosDeDolor: Int,
    val rutinas: Int,
    val tolerancias: Int,
    val referenciasDeMovimiento: Int,
    val pacientesACargo: Int,
    val plantillasDeRutina: Int
)
