package io.github.demiterhugo.debuglens.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.demiterhugo.debuglens.data.repository.InMemoryDiagnosticHistoryRepository
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticHistoryRepository
import io.github.demiterhugo.debuglens.domain.usecase.AnalyzeDiagnosticUseCase
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
    fun provideHistoryRepository(): DiagnosticHistoryRepository = InMemoryDiagnosticHistoryRepository()

    @Provides
    @AnalysisDispatcher
    fun provideAnalysisDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
