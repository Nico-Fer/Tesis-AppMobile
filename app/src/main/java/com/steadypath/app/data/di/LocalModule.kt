package com.steadypath.app.data.di

import android.content.Context
import androidx.room.Room
import com.steadypath.app.data.local.BaseLocalDelUsuario
import com.steadypath.app.data.local.DomiDatabase
import com.steadypath.app.data.local.EjercicioDao
import com.steadypath.app.data.local.InstantaneaDao
import com.steadypath.app.data.local.OperacionPendienteDao
import com.steadypath.app.data.local.RutinaDao
import com.steadypath.app.data.local.SesionDao
import com.steadypath.app.data.local.ToleranciaPropiaDao
import com.steadypath.app.data.session.UserScopedCache
import com.steadypath.app.data.sync.ColaDeOperaciones
import com.steadypath.app.data.sync.ColaDeOperacionesRoom
import com.steadypath.app.data.sync.Sincronizador
import com.steadypath.app.domain.repository.SyncStatusRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Bindings del modo sin conexión: base local, cola de salida y estado de sincronización.
 * Sigue el estilo de [NetworkModule] (`@Provides` explícitos, sin `@Binds`).
 */
@Module
@InstallIn(SingletonComponent::class)
object LocalModule {

    /**
     * Recreación destructiva: todo lo guardado se puede volver a bajar. Lo único que se
     * perdería ante un cambio de esquema sin migración es la cola de salida, así que el
     * día que la versión suba hay que escribir la migración en vez de confiar en esto.
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DomiDatabase =
        Room.databaseBuilder(context, DomiDatabase::class.java, DomiDatabase.NOMBRE)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideEjercicioDao(base: DomiDatabase): EjercicioDao = base.ejercicioDao()

    @Provides
    fun provideRutinaDao(base: DomiDatabase): RutinaDao = base.rutinaDao()

    @Provides
    fun provideSesionDao(base: DomiDatabase): SesionDao = base.sesionDao()

    @Provides
    fun provideToleranciaPropiaDao(base: DomiDatabase): ToleranciaPropiaDao = base.toleranciaPropiaDao()

    @Provides
    fun provideInstantaneaDao(base: DomiDatabase): InstantaneaDao = base.instantaneaDao()

    @Provides
    fun provideOperacionPendienteDao(base: DomiDatabase): OperacionPendienteDao =
        base.operacionPendienteDao()

    @Provides
    @Singleton
    fun provideColaDeOperaciones(cola: ColaDeOperacionesRoom): ColaDeOperaciones = cola

    @Provides
    @Singleton
    fun provideSyncStatusRepository(sincronizador: Sincronizador): SyncStatusRepository = sincronizador

    /** Datos de salud y cola de salida del usuario: no sobreviven al cierre de sesión. */
    @Provides
    @IntoSet
    fun provideBaseLocalUserCache(base: BaseLocalDelUsuario): UserScopedCache = base
}
