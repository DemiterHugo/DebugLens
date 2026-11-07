package io.github.demiterhugo.debuglens.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.demiterhugo.debuglens.data.repository.AndroidDiagnosticReportWriter
import io.github.demiterhugo.debuglens.data.repository.AndroidLogcatCaptureRepository
import io.github.demiterhugo.debuglens.data.repository.AndroidLogcatImportRepository
import io.github.demiterhugo.debuglens.data.repository.DiagnosticHistoryStorage
import io.github.demiterhugo.debuglens.data.repository.LiteRtDiagnosticAiRepository
import io.github.demiterhugo.debuglens.domain.repository.AiModelRepository
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticAdviceGenerator
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticHistoryRepository
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticReportWriter
import io.github.demiterhugo.debuglens.domain.repository.LogcatCaptureRepository
import io.github.demiterhugo.debuglens.domain.repository.LogcatImportRepository
import io.github.demiterhugo.debuglens.domain.usecase.AnalyzeDiagnosticUseCase
import io.github.demiterhugo.debuglens.domain.usecase.GenerateDiagnosticAdviceUseCase
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AnalysisDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DiagnosticModule {
    @Provides
    fun provideAnalyzer(): AnalyzeDiagnosticUseCase = AnalyzeDiagnosticUseCase()

    @Provides
    @Singleton
    fun provideHistoryStorage(@ApplicationContext context: Context): DiagnosticHistoryStorage =
        DiagnosticHistoryStorage.open(context)

    @Provides
    fun provideHistoryRepository(storage: DiagnosticHistoryStorage): DiagnosticHistoryRepository = storage.repository

    @Provides
    fun provideLogcatImporter(@ApplicationContext context: Context): LogcatImportRepository =
        AndroidLogcatImportRepository(context.contentResolver)

    @Provides
    fun provideLogcatCapture(): LogcatCaptureRepository = AndroidLogcatCaptureRepository()

    @Provides
    fun provideReportWriter(@ApplicationContext context: Context): DiagnosticReportWriter =
        AndroidDiagnosticReportWriter(context.contentResolver)

    @Provides
    @Singleton
    fun provideLiteRtDiagnosticAi(@ApplicationContext context: Context): LiteRtDiagnosticAiRepository =
        LiteRtDiagnosticAiRepository(context)

    @Provides
    fun provideAiModels(backend: LiteRtDiagnosticAiRepository): AiModelRepository = backend

    @Provides
    fun provideAdviceGenerator(backend: LiteRtDiagnosticAiRepository): DiagnosticAdviceGenerator = backend

    @Provides
    fun provideGenerateAdvice(generator: DiagnosticAdviceGenerator): GenerateDiagnosticAdviceUseCase =
        GenerateDiagnosticAdviceUseCase(generator)

    @Provides
    @AnalysisDispatcher
    fun provideAnalysisDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
