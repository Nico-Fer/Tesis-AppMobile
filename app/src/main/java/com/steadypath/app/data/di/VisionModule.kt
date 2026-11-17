package com.steadypath.app.data.di

import android.util.Log
import com.steadypath.app.BuildConfig
import com.steadypath.app.data.comparison.CsvRepMetricsSink
import com.steadypath.app.data.comparison.DtwMovementComparator
import com.steadypath.app.data.comparison.LocalAssetReferenceRepository
import com.steadypath.app.data.comparison.RemoteFirstReferenceRepository
import com.steadypath.app.data.session.UserScopedCache
import com.steadypath.app.data.vision.PoseEstimator
import com.steadypath.app.data.vision.PoseModelSpec
import com.steadypath.app.data.vision.YoloPoseEstimator
import com.steadypath.app.domain.comparison.AngleExtractor
import com.steadypath.app.domain.comparison.DefaultAngleExtractor
import com.steadypath.app.domain.comparison.MovementComparator
import com.steadypath.app.domain.comparison.PeakValleyRepetitionSegmenter
import com.steadypath.app.domain.comparison.RepMetricsSink
import com.steadypath.app.domain.comparison.RepetitionSegmenterFactory
import com.steadypath.app.domain.repository.MovementReferenceRepository
import com.steadypath.app.domain.repository.PatientToleranceRepository
import com.steadypath.app.domain.repository.ReferenceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Bindings de la capa de visión: estimador de pose y comparación de movimiento (DTW).
 * Sigue el estilo de [NetworkModule] (`@Provides` explícitos, sin `@Binds`).
 */
@Module
@InstallIn(SingletonComponent::class)
object VisionModule {

    /**
     * Contrato del modelo de pose en uso. Punto único de cambio al reemplazar el `.tflite`:
     * normalmente basta con editar [PoseModelSpec.ACTIVE].
     */
    @Provides
    @Singleton
    fun providePoseModelSpec(): PoseModelSpec = PoseModelSpec.ACTIVE

    @Provides
    @Singleton
    fun providePoseEstimator(impl: YoloPoseEstimator): PoseEstimator = impl

    @Provides
    @Singleton
    fun provideAngleExtractor(): AngleExtractor = DefaultAngleExtractor()

    @Provides
    @Singleton
    fun provideRepetitionSegmenterFactory(): RepetitionSegmenterFactory =
        RepetitionSegmenterFactory { driverAngleIndex, driverAmplitudeRad ->
            PeakValleyRepetitionSegmenter.tunedFor(driverAngleIndex, driverAmplitudeRad)
        }

    /** API-first con fallback a assets; [LocalAssetReferenceRepository] queda como última capa. */
    @Provides
    @Singleton
    fun provideReferenceRepository(impl: RemoteFirstReferenceRepository): ReferenceRepository = impl

    @Provides
    @Singleton
    fun provideMovementReferenceRepository(
        impl: RemoteFirstReferenceRepository
    ): MovementReferenceRepository = impl

    /** Las referencias propias del paciente se descartan al cerrar sesión. */
    @Provides
    @IntoSet
    fun provideReferenceUserCache(impl: RemoteFirstReferenceRepository): UserScopedCache = impl

    /** CSV de métricas por rep solo en DEBUG (protocolo de calibración); no-op en release. */
    @Provides
    @Singleton
    fun provideRepMetricsSink(csv: CsvRepMetricsSink): RepMetricsSink =
        if (BuildConfig.DEBUG) csv else RepMetricsSink.NO_OP

    /** Para volver al stub: reemplazar por `NoOpMovementComparator()`. */
    @Provides
    @Singleton
    fun provideMovementComparator(
        referenceRepository: ReferenceRepository,
        angleExtractor: AngleExtractor,
        segmenterFactory: RepetitionSegmenterFactory,
        toleranceRepository: PatientToleranceRepository,
        metricsSink: RepMetricsSink
    ): MovementComparator = DtwMovementComparator(
        referenceRepository = referenceRepository,
        angleExtractor = angleExtractor,
        segmenterFactory = segmenterFactory,
        toleranceRepository = toleranceRepository,
        // CPU-bound (matriz DTW): Default, no el IO sin qualifier de NetworkModule.
        computeDispatcher = Dispatchers.Default,
        log = { message -> Log.w("DtwComparator", message) },
        metricsSink = metricsSink
    )
}
