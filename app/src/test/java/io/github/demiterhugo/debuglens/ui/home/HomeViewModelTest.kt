package io.github.demiterhugo.debuglens.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis
import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.model.DiagnosticInputError
import io.github.demiterhugo.debuglens.domain.model.DiagnosticResult
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticHistoryRepository
import io.github.demiterhugo.debuglens.domain.repository.LogcatCaptureRepository
import io.github.demiterhugo.debuglens.domain.repository.LogcatImportRepository
import io.github.demiterhugo.debuglens.domain.repository.LogcatIncidentResult
import io.github.demiterhugo.debuglens.domain.usecase.AnalyzeDiagnosticUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
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
    fun failedSavingKeepsTheDiagnosticAvailableAndTheNextAttemptCanSucceed() = runTest {
        repository.beforeSave = { error("Storage unavailable") }
        val model = createModel()
        model.updateInput("java.lang.NullPointerException")
        model.analyze()
        advanceUntilIdle()

        assertEquals(HomeOperationError.HISTORY_SAVE, model.uiState.value.operationError)
        assertEquals(DiagnosticCategory.NULL_REFERENCE, model.uiState.value.analysis?.category)
        assertTrue(model.uiState.value.selectedSession!!.id.startsWith("unsaved-"))
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
    fun historyFiltersFollowNewSnapshotsWithoutChangingTheOpenDiagnosis() = runTest {
        val model = createModel()
        model.updateInput("java.lang.NullPointerException: café")
        model.analyze()
        advanceUntilIdle()
        val first = model.uiState.value.recentSessions.single()

        model.updateHistoryQuery("CAFE")
        runCurrent()
        assertEquals(listOf(first), model.uiState.value.matchingSessions)
        model.selectHistoryCategory(DiagnosticCategory.INVALID_STATE)
        runCurrent()
        assertTrue(model.uiState.value.matchingSessions.isEmpty())
        assertEquals(first, model.uiState.value.selectedSession)
        assertEquals(listOf(first), model.uiState.value.recentSessions)

        model.updateInput("java.lang.IllegalStateException: café")
        model.analyze()
        advanceUntilIdle()
        val second = model.uiState.value.recentSessions.first()
        assertEquals(listOf(second), model.uiState.value.matchingSessions)

        model.resetHistoryFilter()
        assertFalse(model.uiState.value.historyFilter.isActive)
        assertEquals(listOf(second, first), model.uiState.value.matchingSessions)
    }

    @Test
    fun historyFilteringWaitsForTheAnalysisDispatcherAndDiscardsAnObsoleteResult() = runTest {
        val analyzer = AnalyzeDiagnosticUseCase()
        repository.save((analyzer("IllegalStateException: first") as DiagnosticResult.Success).analysis)
        val latest = repository.save((analyzer("IllegalStateException: latest") as DiagnosticResult.Success).analysis)
        val worker = StandardTestDispatcher(TestCoroutineScheduler())
        val model = createModel(analysisDispatcher = worker)
        runCurrent()
        worker.scheduler.runCurrent()
        runCurrent()

        model.updateHistoryQuery("first")
        runCurrent()
        assertEquals("first", model.uiState.value.historyFilter.query)
        assertTrue(model.uiState.value.isHistorySearching)
        assertTrue(model.uiState.value.matchingSessions.isEmpty())

        worker.scheduler.runCurrent()
        model.updateHistoryQuery("latest")
        runCurrent()
        assertTrue(model.uiState.value.isHistorySearching)
        assertTrue(model.uiState.value.matchingSessions.isEmpty())

        worker.scheduler.runCurrent()
        runCurrent()
        assertFalse(model.uiState.value.isHistorySearching)
        assertEquals(listOf(latest), model.uiState.value.matchingSessions)
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

        assertTrue(model.uiState.value.historyLoadError)
        assertFalse(model.uiState.value.isHistoryLoading)
    }

    @Test
    fun showsLoadingUntilTheInitialHistorySnapshotArrives() = runTest {
        val ready = CompletableDeferred<Unit>()
        repository.beforeObservation = { ready.await() }
        val model = createModel()
        runCurrent()
        assertTrue(model.uiState.value.isHistoryLoading)
        ready.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.uiState.value.isHistoryLoading)
        assertFalse(model.uiState.value.historyLoadError)
    }

    @Test
    fun historyObservationCanBeRetriedAndItsErrorSurvivesDraftEdits() = runTest {
        repository.failObservation = true
        val model = createModel()
        advanceUntilIdle()
        model.updateInput("java.lang.NullPointerException")
        assertTrue(model.uiState.value.historyLoadError)

        repository.failObservation = false
        model.retryHistory()
        advanceUntilIdle()
        model.analyze()
        advanceUntilIdle()
        assertFalse(model.uiState.value.historyLoadError)
        assertFalse(model.uiState.value.isHistoryLoading)
        assertEquals(1, model.uiState.value.recentSessions.size)
    }

    @Test
    fun clearingCannotOverlapAnotherClearOrANewAnalysis() = runTest {
        val ready = CompletableDeferred<Unit>()
        repository.beforeClear = { ready.await() }
        val model = createModel()
        advanceUntilIdle()
        model.updateInput("java.lang.NullPointerException")
        model.clearHistory()
        runCurrent()
        assertTrue(model.uiState.value.isClearingHistory)
        model.clearHistory()
        model.analyze()
        runCurrent()
        assertEquals(1, repository.clearCalls)
        assertEquals(0, repository.saveCalls)

        ready.complete(Unit)
        advanceUntilIdle()
        assertFalse(model.uiState.value.isClearingHistory)
        model.analyze()
        advanceUntilIdle()
        assertEquals(1, repository.saveCalls)
    }

    @Test
    fun failedClearingCanBeRetriedWithoutDiscardingTheResult() = runTest {
        val model = createModel()
        model.updateInput("java.lang.NullPointerException")
        model.analyze()
        advanceUntilIdle()
        val selected = model.uiState.value.selectedSession
        repository.beforeClear = { error("Disk unavailable") }
        model.clearHistory()
        advanceUntilIdle()
        assertEquals(HomeOperationError.HISTORY_CLEAR, model.uiState.value.operationError)
        assertFalse(model.uiState.value.isClearingHistory)
        assertEquals(selected, model.uiState.value.selectedSession)

        repository.beforeClear = {}
        model.clearHistory()
        advanceUntilIdle()
        assertNull(model.uiState.value.operationError)
        assertTrue(model.uiState.value.recentSessions.isEmpty())
        assertEquals(selected, model.uiState.value.selectedSession)
    }

    @Test
    fun successiveUnsavedResultsHaveDifferentSurfaceIdentities() = runTest {
        repository.beforeSave = { error("Disk unavailable") }
        val model = createModel()
        model.updateInput("java.lang.NullPointerException")
        model.analyze()
        advanceUntilIdle()
        val firstId = model.uiState.value.selectedSession!!.id
        model.analyze()
        advanceUntilIdle()
        assertNotEquals(firstId, model.uiState.value.selectedSession!!.id)
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

    @Test
    fun importedLogcatIsSavedOnlyAfterTheUserAnalyzesIt() = runTest {
        val trace = "11-07 10:11:12.100  2200  2200 E AndroidRuntime: java.lang.IllegalStateException: sample"
        val model = createModel(LogcatImportRepository { LogcatIncidentResult.Found(trace) })

        model.importLogcat("selected-document")
        advanceUntilIdle()

        assertEquals(trace, model.uiState.value.input)
        assertNull(model.uiState.value.selectedSession)
        assertEquals(0, repository.saveCalls)

        model.analyze()
        advanceUntilIdle()
        assertEquals(DiagnosticCategory.INVALID_STATE, model.uiState.value.analysis?.category)
        assertEquals(1, repository.saveCalls)
    }

    @Test
    fun failedLogcatImportPreservesTheCurrentDraftAndAnalysis() = runTest {
        val model = createModel(LogcatImportRepository { LogcatIncidentResult.Failure.NO_INCIDENT })
        val draft = "java.lang.NullPointerException"
        model.updateInput(draft)
        model.analyze()
        advanceUntilIdle()
        val selected = model.uiState.value.selectedSession

        model.importLogcat("empty-log")
        advanceUntilIdle()

        assertEquals(draft, model.uiState.value.input)
        assertEquals(selected, model.uiState.value.selectedSession)
        assertEquals(
            LogcatReadError(LogcatSource.DOCUMENT, LogcatIncidentResult.Failure.NO_INCIDENT),
            model.uiState.value.logcatReadError,
        )
        assertNull(model.uiState.value.logcatReadInProgress)
    }

    @Test
    fun editingTheDraftCancelsAnImportAndPreventsAStaleResult() = runTest {
        val model = createModel(LogcatImportRepository {
            try {
                awaitCancellation()
            } catch (_: kotlinx.coroutines.CancellationException) {
                LogcatIncidentResult.Found("late result")
            }
        })
        model.importLogcat("slow-document")
        runCurrent()
        assertEquals(LogcatSource.DOCUMENT, model.uiState.value.logcatReadInProgress)

        model.updateInput("new draft")
        advanceUntilIdle()
        assertEquals("new draft", model.uiState.value.input)
        assertNull(model.uiState.value.logcatReadError)
        assertNull(model.uiState.value.logcatReadInProgress)
    }

    @Test
    fun capturingLogcatAnalyzesAndSavesTheIncidentOnce() = runTest {
        val trace = "11-07 10:11:12.100  2200  2200 E AndroidRuntime: java.lang.IllegalStateException: captured"
        var reads = 0
        val model = createModel(capture = LogcatCaptureRepository {
            reads++
            LogcatIncidentResult.Found(trace)
        })

        model.captureLogcat()
        model.captureLogcat()
        assertEquals(LogcatSource.DEVICE, model.uiState.value.logcatReadInProgress)
        advanceUntilIdle()

        assertEquals(1, reads)
        assertEquals(trace, model.uiState.value.input)
        assertEquals(DiagnosticCategory.INVALID_STATE, model.uiState.value.analysis?.category)
        assertEquals(model.uiState.value.recentSessions.single(), model.uiState.value.selectedSession)
        assertEquals(1, repository.saveCalls)
        assertFalse(model.uiState.value.isInputBusy)
    }

    @Test
    fun recapturingTheSameLogcatIncidentReusesItsSession() = runTest {
        val trace = "11-07 10:11:12.100  2200  2200 E AndroidRuntime: java.lang.IllegalStateException: captured"
        var capturedTrace = trace
        val model = createModel(capture = LogcatCaptureRepository { LogcatIncidentResult.Found(capturedTrace) })

        model.captureLogcat()
        advanceUntilIdle()
        val firstSession = model.uiState.value.recentSessions.single()

        model.captureLogcat()
        advanceUntilIdle()
        assertEquals(firstSession, model.uiState.value.selectedSession)
        assertEquals(listOf(firstSession), model.uiState.value.recentSessions)
        assertEquals(1, repository.saveCalls)

        capturedTrace = trace.replace("12.100", "13.100")
        model.captureLogcat()
        advanceUntilIdle()
        assertNotEquals(firstSession, model.uiState.value.selectedSession)
        assertEquals(2, model.uiState.value.recentSessions.size)
        assertEquals(2, repository.saveCalls)
    }

    @Test
    fun emptyCapturePreservesTheCurrentDiagnosticAndHistory() = runTest {
        val model = createModel(capture = LogcatCaptureRepository { LogcatIncidentResult.Failure.NO_INCIDENT })
        val draft = "java.lang.NullPointerException: previous"
        model.updateInput(draft)
        model.analyze()
        advanceUntilIdle()
        val selected = model.uiState.value.selectedSession

        model.captureLogcat()
        advanceUntilIdle()

        assertEquals(draft, model.uiState.value.input)
        assertEquals(selected, model.uiState.value.selectedSession)
        assertEquals(
            LogcatReadError(LogcatSource.DEVICE, LogcatIncidentResult.Failure.NO_INCIDENT),
            model.uiState.value.logcatReadError,
        )
        assertEquals(1, repository.saveCalls)
        assertFalse(model.uiState.value.isInputBusy)
    }

    @Test
    fun cancelingCaptureKeepsTheDraftAndDiscardsLateResults() = runTest {
        val model = createModel(capture = LogcatCaptureRepository {
            try {
                awaitCancellation()
            } catch (_: kotlinx.coroutines.CancellationException) {
                LogcatIncidentResult.Found("java.lang.IllegalStateException: late")
            }
        })
        model.updateInput("current draft")
        model.captureLogcat()
        runCurrent()

        model.cancelLogcatCapture()
        advanceUntilIdle()

        assertEquals("current draft", model.uiState.value.input)
        assertNull(model.uiState.value.logcatReadError)
        assertFalse(model.uiState.value.isInputBusy)
        assertEquals(0, repository.saveCalls)
    }

    private fun createModel(
        importer: LogcatImportRepository = LogcatImportRepository { error("Unexpected import") },
        capture: LogcatCaptureRepository = LogcatCaptureRepository { error("Unexpected capture") },
        analysisDispatcher: CoroutineDispatcher = mainDispatcherRule.dispatcher,
    ): HomeViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                HomeViewModel(AnalyzeDiagnosticUseCase(), repository, importer, capture, analysisDispatcher) as T
        }
        return ViewModelProvider(store, factory)[HomeViewModel::class.java]
    }

    private class FakeHistoryRepository : DiagnosticHistoryRepository {
        private val sessions = MutableStateFlow<List<DiagnosticSession>>(emptyList())
        var beforeSave: suspend () -> Unit = {}
        var saveCalls = 0
        var failObservation = false
        var beforeObservation: suspend () -> Unit = {}
        var beforeClear: suspend () -> Unit = {}
        var clearCalls = 0

        override fun observeSessions(): Flow<List<DiagnosticSession>> =
            flow {
                beforeObservation()
                if (failObservation) error("History unavailable")
                emitAll(sessions)
            }

        override suspend fun save(analysis: DiagnosticAnalysis): DiagnosticSession {
            saveCalls++
            beforeSave()
            val session = DiagnosticSession("session-$saveCalls", analysis)
            sessions.value = listOf(session) + sessions.value
            return session
        }

        override suspend fun saveOrReuse(analysis: DiagnosticAnalysis): DiagnosticSession =
            sessions.value.firstOrNull { it.analysis.input == analysis.input } ?: save(analysis)

        override suspend fun clear() {
            clearCalls++
            beforeClear()
            sessions.value = emptyList()
        }
    }
}
