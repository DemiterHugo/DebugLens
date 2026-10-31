package io.github.demiterhugo.debuglens.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis
import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.model.DiagnosticInputError
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticHistoryRepository
import io.github.demiterhugo.debuglens.domain.usecase.AnalyzeDiagnosticUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()
    private val store = ViewModelStore()
    private val repository = FakeHistoryRepository()

    @After
    fun clearViewModels() {
        store.clear()
    }

    @Test
    fun publishesAnalysisAndObservesTheSavedHistory() = runTest {
        val model = createModel()
        model.updateInput("java.lang.NullPointerException: missing")
        model.analyze()
        advanceUntilIdle()

        assertEquals(DiagnosticCategory.NULL_REFERENCE, model.uiState.value.analysis?.category)
        assertEquals(1, model.uiState.value.recentSessions.size)
        assertFalse(model.uiState.value.isAnalyzing)
    }

    @Test
    fun invalidInputIsNeverSaved() = runTest {
        val model = createModel()
        model.updateInput(" \n ")
        model.analyze()
        advanceUntilIdle()

        assertEquals(DiagnosticInputError.EMPTY, model.uiState.value.inputError)
        assertEquals(0, repository.saveCalls)
        assertNull(model.uiState.value.analysis)
    }

    @Test
    fun repeatedClicksDuringAnalysisDoNotCreateDuplicateEntries() = runTest {
        val model = createModel()
        model.updateInput("java.lang.NullPointerException")
        model.analyze()
        model.analyze()
        advanceUntilIdle()

        assertEquals(1, repository.saveCalls)
    }

    @Test
    fun changingInputCancelsTheOldRequestAndAllowsANewAnalysis() = runTest {
        val model = createModel()
        model.updateInput("java.lang.NullPointerException")
        model.analyze()
        model.updateInput("java.lang.IllegalStateException: newer")
        advanceUntilIdle()

        assertEquals(0, repository.saveCalls)
        assertNull(model.uiState.value.analysis)
        assertFalse(model.uiState.value.isAnalyzing)

        model.analyze()
        advanceUntilIdle()
        assertEquals(DiagnosticCategory.INVALID_STATE, model.uiState.value.analysis?.category)
        assertEquals(1, repository.saveCalls)
    }

    @Test
    fun cancellationWhileSavingDoesNotBecomeAnErrorOrPublishStaleResults() = runTest {
        repository.beforeSave = { awaitCancellation() }
        val model = createModel()
        model.updateInput("java.lang.NullPointerException")
        model.analyze()
        runCurrent()
        assertTrue(model.uiState.value.isAnalyzing)

        model.updateInput("A different draft")
        advanceUntilIdle()

        assertNull(model.uiState.value.operationError)
        assertNull(model.uiState.value.analysis)
        assertFalse(model.uiState.value.isAnalyzing)
        assertTrue(model.uiState.value.recentSessions.isEmpty())
    }

    @Test
    fun failedSavingIsReportedAndTheNextAttemptCanSucceed() = runTest {
        repository.beforeSave = { error("Storage unavailable") }
        val model = createModel()
        model.updateInput("java.lang.NullPointerException")
        model.analyze()
        advanceUntilIdle()

        assertEquals(HomeOperationError.ANALYSIS, model.uiState.value.operationError)
        assertFalse(model.uiState.value.isAnalyzing)

        repository.beforeSave = {}
        model.analyze()
        advanceUntilIdle()
        assertNull(model.uiState.value.operationError)
        assertEquals(DiagnosticCategory.NULL_REFERENCE, model.uiState.value.analysis?.category)
    }

    @Test
    fun reopeningHistoryRestoresTheAnalyzedTextAndResult() = runTest {
        val model = createModel()
        model.updateInput("  java.lang.NullPointerException: saved  ")
        model.analyze()
        advanceUntilIdle()
        val session = model.uiState.value.recentSessions.single()
        model.updateInput("another draft")

        model.openSession(session.id)

        assertEquals("java.lang.NullPointerException: saved", model.uiState.value.input)
        assertEquals(session.analysis, model.uiState.value.analysis)
    }

    @Test
    fun clearingHistoryKeepsTheCurrentlyDisplayedResult() = runTest {
        val model = createModel()
        model.updateInput("java.lang.NullPointerException")
        model.analyze()
        advanceUntilIdle()
        val analysis = model.uiState.value.analysis

        model.clearHistory()
        advanceUntilIdle()

        assertTrue(model.uiState.value.recentSessions.isEmpty())
        assertEquals(analysis, model.uiState.value.analysis)
    }

    @Test
    fun reportsHistoryObservationFailures() = runTest {
        repository.failObservation = true
        val model = createModel()
        advanceUntilIdle()

        assertEquals(HomeOperationError.HISTORY, model.uiState.value.operationError)
    }

    @Test
    fun missingHistoryIdsDoNotReplaceTheCurrentDraft() = runTest {
        val model = createModel()
        model.updateInput("current draft")
        model.openSession("not-found")

        assertEquals("current draft", model.uiState.value.input)
    }

    @Test
    fun repeatingTheSameAnalysisSelectsANewSessionIdentity() = runTest {
        val model = createModel()
        model.updateInput("java.lang.NullPointerException")
        model.analyze()
        advanceUntilIdle()
        val firstSessionId = model.uiState.value.selectedSession?.id

        model.analyze()
        advanceUntilIdle()

        assertNotEquals(firstSessionId, model.uiState.value.selectedSession?.id)
        assertEquals(2, model.uiState.value.recentSessions.size)
    }

    private fun createModel(): HomeViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(AnalyzeDiagnosticUseCase(), repository, mainDispatcherRule.dispatcher) as T
        }
        return ViewModelProvider(store, factory)[HomeViewModel::class.java]
    }

    private class FakeHistoryRepository : DiagnosticHistoryRepository {
        private val sessions = MutableStateFlow<List<DiagnosticSession>>(emptyList())
        var beforeSave: suspend () -> Unit = {}
        var saveCalls = 0
        var failObservation = false

        override fun observeSessions(): Flow<List<DiagnosticSession>> =
            if (failObservation) flow { error("History unavailable") } else sessions

        override suspend fun save(analysis: DiagnosticAnalysis): DiagnosticSession {
            saveCalls++
            beforeSave()
            val session = DiagnosticSession("session-$saveCalls", analysis)
            sessions.value = listOf(session) + sessions.value
            return session
        }

        override suspend fun clear() {
            sessions.value = emptyList()
        }
    }
}
