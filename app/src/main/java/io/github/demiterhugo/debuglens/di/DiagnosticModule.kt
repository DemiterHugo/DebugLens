package io.github.demiterhugo.debuglens.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.demiterhugo.debuglens.data.repository.DiagnosticHistoryStorage
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
    fun provideHistoryStorage(@ApplicationContext context: Context): DiagnosticHistoryStorage =
        DiagnosticHistoryStorage.open(context)

    @Provides
    fun provideHistoryRepository(storage: DiagnosticHistoryStorage): DiagnosticHistoryRepository = storage.repository

    @Provides
    @AnalysisDispatcher
    fun provideAnalysisDispatcher(): CoroutineDispatcher = Dispatchers.Default
}
