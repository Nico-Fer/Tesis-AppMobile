package com.steadypath.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.steadypath.app.data.session.UserScopedCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Base local del modo sin conexión. Sin migraciones todavía: es la versión 1 y, como todo
 * su contenido se puede volver a bajar o ya está en la cola de salida, el módulo de DI la
 * crea con recreación destructiva solo para bajar de versión.
 */
@Database(
    entities = [
        EjercicioEntity::class,
        RutinaEntity::class,
        RutinaEjercicioEntity::class,
        SesionEntity::class,
        ResultadoSesionEntity::class,
        ToleranciaPropiaEntity::class,
        InstantaneaEntity::class,
        OperacionPendienteEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DomiDatabase : RoomDatabase() {
    abstract fun ejercicioDao(): EjercicioDao
    abstract fun rutinaDao(): RutinaDao
    abstract fun sesionDao(): SesionDao
    abstract fun toleranciaPropiaDao(): ToleranciaPropiaDao
    abstract fun instantaneaDao(): InstantaneaDao
    abstract fun operacionPendienteDao(): OperacionPendienteDao

    companion object {
        const val NOMBRE = "domi.db"
    }
}

/**
 * Lo guardado es de quien tiene la sesión abierta, e incluye datos de salud: al cerrar
 * sesión se vacía todo, también lo que no llegó a subirse (misma política que ya tenía la
 * cola de sesiones).
 */
@Singleton
class BaseLocalDelUsuario @Inject constructor(
    private val base: DomiDatabase,
    private val dispatcher: CoroutineDispatcher
) : UserScopedCache {
    override suspend fun clear() = withContext(dispatcher) { base.clearAllTables() }
}
