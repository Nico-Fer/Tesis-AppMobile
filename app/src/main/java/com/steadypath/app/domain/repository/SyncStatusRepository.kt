package com.steadypath.app.domain.repository

import com.steadypath.app.domain.model.EstadoDeSincronizacion
import kotlinx.coroutines.flow.Flow

/** Estado del modo sin conexión para la interfaz; la capa de datos decide cómo se calcula. */
interface SyncStatusRepository {
    val estado: Flow<EstadoDeSincronizacion>

    /** La persona ya leyó que hubo cambios rechazados: deja de avisarse. */
    fun avisoDeRechazoVisto()
}
