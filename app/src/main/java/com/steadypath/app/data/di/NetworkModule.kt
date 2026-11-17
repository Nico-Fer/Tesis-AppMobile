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
import com.steadypath.app.data.session.TextScalePreferencesStore
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
import com.steadypath.app.domain.repository.TextScalePreferencesRepository
import com.steadypath.app.domain.repository.SessionRepository
import com.steadypath.app.domain.repository.UserSessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
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
    fun provideTextScalePreferencesRepository(
        store: TextScalePreferencesStore
    ): TextScalePreferencesRepository = store

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
        dispatcher: CoroutineDispatcher,
        gson: Gson
    ): AuthRepository = AuthRepositoryImpl(
        cognito, apiService, sessionRepository, pendingRegistration, userCaches, dispatcher, gson
    )

    /**
     * Mismo singleton que [provideAuthRepository] (memoizado por Hilt), expuesto bajo el
     * seam angosto que consume la capa de comparación de movimiento.
     */
    @Provides
    @Singleton
    fun providePatientToleranceRepository(
        authRepository: AuthRepository
    ): PatientToleranceRepository = authRepository

    @Provides
    @Singleton
    fun provideExerciseRepository(
        apiService: RehabApiService,
        caller: SafeApiCaller
    ): ExerciseRepository = ExerciseRepositoryImpl(apiService, caller)

    @Provides
    @Singleton
    fun provideSessionRepository(
        apiService: RehabApiService,
        caller: SafeApiCaller
    ): SessionRepository = SessionRepositoryImpl(apiService, caller)

    @Provides
    @Singleton
    fun provideRoutineRepository(
        apiService: RehabApiService,
        caller: SafeApiCaller
    ): RoutineRepository = RoutineRepositoryImpl(apiService, caller)

    @Provides
    @Singleton
    fun provideMedicRepository(
        apiService: RehabApiService,
        caller: SafeApiCaller
    ): MedicRepository = MedicRepositoryImpl(apiService, caller)
}
