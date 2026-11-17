package com.steadypath.app.data.di

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.steadypath.app.BuildConfig
import com.steadypath.app.data.auth.AuthInterceptor
import com.steadypath.app.data.auth.CognitoAuthService
import com.steadypath.app.data.auth.TokenAuthenticator
import com.steadypath.app.data.datasource.RehabApiService
import com.steadypath.app.data.datasource.SafeApiCaller
import com.steadypath.app.data.repository.AuthRepositoryImpl
import com.steadypath.app.data.repository.ExerciseRepositoryImpl
import com.steadypath.app.data.repository.MedicRepositoryImpl
import com.steadypath.app.data.repository.RoutineRepositoryImpl
import com.steadypath.app.data.repository.SessionRepositoryImpl
import com.steadypath.app.data.session.NotificationPreferencesStore
import com.steadypath.app.data.session.PatientNotificationPreferencesStore
import com.steadypath.app.data.session.PendingRegistrationHolder
import com.steadypath.app.data.session.SessionCameraHolder
import com.steadypath.app.data.session.CameraPreferencesStore
import com.steadypath.app.data.session.SesionesPendientesStore
import com.steadypath.app.data.local.LecturaConCache
import com.steadypath.app.data.local.ToleranciaConCache
import com.steadypath.app.data.local.ToleranciaPropiaDao
import com.steadypath.app.data.sync.ColaDeOperaciones
import com.steadypath.app.data.sync.EstadoDeLaSesion
import com.steadypath.app.data.sync.MonitorDeConectividad
import com.steadypath.app.data.sync.Sincronizador
import com.steadypath.app.data.session.UserScopedCache
import com.steadypath.app.data.session.UserSessionStore
import com.steadypath.app.domain.repository.AuthRepository
import com.steadypath.app.domain.repository.ExerciseRepository
import com.steadypath.app.domain.repository.MedicRepository
import com.steadypath.app.domain.repository.NotificationPreferencesRepository
import com.steadypath.app.domain.repository.PatientNotificationPreferencesRepository
import com.steadypath.app.domain.repository.PatientToleranceRepository
import com.steadypath.app.domain.repository.RoutineRepository
import com.steadypath.app.domain.repository.SessionCameraRepository
import com.steadypath.app.domain.repository.CameraPreferencesRepository
import com.steadypath.app.domain.repository.SessionRepository
import com.steadypath.app.domain.repository.UserSessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().create()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: TokenAuthenticator
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideRehabApiService(retrofit: Retrofit): RehabApiService {
        return retrofit.create(RehabApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Singleton
    fun provideUserSessionRepository(store: UserSessionStore): UserSessionRepository = store

    @Provides
    @Singleton
    fun provideNotificationPreferencesRepository(
        store: NotificationPreferencesStore
    ): NotificationPreferencesRepository = store

    @Provides
    @Singleton
    fun providePatientNotificationPreferencesRepository(
        store: PatientNotificationPreferencesStore
    ): PatientNotificationPreferencesRepository = store

    @Provides
    @Singleton
    fun provideCameraPreferencesRepository(
        store: CameraPreferencesStore
    ): CameraPreferencesRepository = store

    @Provides
    @Singleton
    fun provideSessionCameraRepository(
        holder: SessionCameraHolder
    ): SessionCameraRepository = holder

    @Provides
    @Singleton
    fun provideAuthRepository(
        cognito: CognitoAuthService,
        apiService: RehabApiService,
        sessionRepository: UserSessionRepository,
        pendingRegistration: PendingRegistrationHolder,
        userCaches: Set<@JvmSuppressWildcards UserScopedCache>,
        sincronizador: Sincronizador,
        caller: SafeApiCaller,
        cola: ColaDeOperaciones,
        monitor: MonitorDeConectividad,
        lectura: LecturaConCache,
        sesion: EstadoDeLaSesion,
        dispatcher: CoroutineDispatcher,
        gson: Gson
    ): AuthRepository = AuthRepositoryImpl(
        cognito, apiService, sessionRepository, pendingRegistration, userCaches, sincronizador,
        caller, cola, monitor, lectura, sesion, dispatcher, gson
    )

    /**
     * Seam angosto que consume la capa de comparación de movimiento. Envuelve al
     * [AuthRepository] con una copia local para que el comparador use la misma tolerancia
     * con y sin red.
     */
    @Provides
    @Singleton
    fun providePatientToleranceRepository(
        authRepository: AuthRepository,
        dao: ToleranciaPropiaDao,
        usuario: UserSessionRepository,
        monitor: MonitorDeConectividad
    ): PatientToleranceRepository = ToleranciaConCache(authRepository, dao, usuario, monitor)

    @Provides
    @Singleton
    fun provideExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository = impl

    @Provides
    @Singleton
    fun provideSessionRepository(impl: SessionRepositoryImpl): SessionRepository = impl

    /** Cola de la versión anterior: se vacía al logout igual que el resto de los datos del usuario. */
    @Provides
    @IntoSet
    fun provideSesionesPendientesUserCache(store: SesionesPendientesStore): UserScopedCache = store

    @Provides
    @Singleton
    fun provideRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository = impl

    @Provides
    @Singleton
    fun provideMedicRepository(impl: MedicRepositoryImpl): MedicRepository = impl
}
