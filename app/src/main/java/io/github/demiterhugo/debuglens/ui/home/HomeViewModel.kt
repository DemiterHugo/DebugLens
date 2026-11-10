package io.github.demiterhugo.debuglens.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.demiterhugo.debuglens.di.AnalysisDispatcher
import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis
import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.model.DiagnosticResult
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticHistoryRepository
import io.github.demiterhugo.debuglens.domain.repository.LogcatCaptureRepository
import io.github.demiterhugo.debuglens.domain.repository.LogcatImportRepository
import io.github.demiterhugo.debuglens.domain.repository.LogcatIncidentResult
import io.github.demiterhugo.debuglens.domain.usecase.AnalyzeDiagnosticUseCase
import io.github.demiterhugo.debuglens.domain.usecase.DiagnosticHistoryFilter
import io.github.demiterhugo.debuglens.domain.usecase.DiagnosticHistorySearch
import javax.inject.Inject
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val analyzeDiagnostic: AnalyzeDiagnosticUseCase,
    private val historyRepository: DiagnosticHistoryRepository,
    private val logcatImporter: LogcatImportRepository,
    private val logcatCapture: LogcatCaptureRepository,
    @param:AnalysisDispatcher private val analysisDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var inputJob: Job? = null
    private var historyJob: Job? = null
    private var historySearchJob: Job? = null
    private var historySearch = DiagnosticHistorySearch(emptyList())
    private var inputRevision = 0L

    init {
        observeHistory()
    }

    fun retryHistory() {
        if (!_uiState.value.isHistoryLoading) observeHistory()
    }

    private fun observeHistory() {
        historyJob?.cancel()
        _uiState.update { it.copy(isHistoryLoading = true, historyLoadError = false) }
        historyJob = viewModelScope.launch {
            try {
                historyRepository.observeSessions().collectLatest { sessions ->
                    historySearch = withContext(analysisDispatcher) { DiagnosticHistorySearch(sessions) }
                    applyHistoryFilter(_uiState.value.historyFilter, sessions)
                    _uiState.update { it.copy(isHistoryLoading = false) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(historyLoadError = true, isHistoryLoading = false) }
            }
        }
    }

    fun updateHistoryQuery(query: String) {
        applyHistoryFilter(_uiState.value.historyFilter.copy(query = query))
    }

    fun selectHistoryCategory(category: DiagnosticCategory?) {
        applyHistoryFilter(_uiState.value.historyFilter.copy(category = category))
    }

    fun resetHistoryFilter() {
        applyHistoryFilter(DiagnosticHistoryFilter())
    }

    private fun applyHistoryFilter(
        filter: DiagnosticHistoryFilter,
        sessions: List<DiagnosticSession> = _uiState.value.recentSessions,
    ) {
        historySearchJob?.cancel()
        val needsSearch = filter.isActive && sessions.isNotEmpty()
        _uiState.update {
            it.copy(
                historyFilter = filter,
                recentSessions = sessions,
                matchingSessions = if (needsSearch) emptyList() else sessions,
                isHistorySearching = needsSearch,
            )
        }
        if (!needsSearch) return

        val search = historySearch
        historySearchJob = viewModelScope.launch {
            val matches = withContext(analysisDispatcher) { search.find(filter) }
            ensureActive()
            _uiState.update { it.copy(matchingSessions = matches, isHistorySearching = false) }
        }
    }

    fun updateInput(input: String) {
        cancelInputOperation()
        replaceInput(input)
    }

    private fun replaceInput(input: String) {
        _uiState.update {
            it.copy(
                input = input,
                inputError = null,
                selectedSession = null,
                operationError = null,
                isAnalyzing = false,
                logcatReadInProgress = null,
                logcatReadError = null,
            )
        }
    }

    fun importLogcat(documentId: String) = readLogcat(LogcatSource.DOCUMENT) {
        logcatImporter.importDocument(documentId)
    }

    fun captureLogcat() = readLogcat(LogcatSource.DEVICE, logcatCapture::captureLatestIncident)

    fun cancelLogcatCapture() {
        if (_uiState.value.logcatReadInProgress != LogcatSource.DEVICE) return
        cancelInputOperation()
        _uiState.update { it.copy(logcatReadInProgress = null) }
    }

    private fun readLogcat(source: LogcatSource, read: suspend () -> LogcatIncidentResult) {
        if (_uiState.value.isInputBusy || _uiState.value.isHistoryMutating) return
        val revision = inputRevision
        _uiState.update { it.copy(logcatReadInProgress = source, logcatReadError = null) }
        inputJob = viewModelScope.launch {
            try {
                val result = read()
                ensureActive()
                when (result) {
                    is LogcatIncidentResult.Found -> {
                        replaceInput(result.trace)
                        if (source == LogcatSource.DEVICE) {
                            beginAnalysis()
                            analyzeInput(result.trace, historyRepository::saveOrReuse)
                        }
                    }
                    is LogcatIncidentResult.Failure -> _uiState.update {
                        it.copy(logcatReadError = LogcatReadError(source, result))
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                _uiState.update {
                    it.copy(logcatReadError = LogcatReadError(source, LogcatIncidentResult.Failure.UNREADABLE))
                }
            } finally {
                if (inputRevision == revision) {
                    _uiState.update { it.copy(logcatReadInProgress = null) }
                }
            }
        }
    }

    fun analyze() {
        if (_uiState.value.isInputBusy || _uiState.value.isHistoryMutating) return
        val input = _uiState.value.input
        beginAnalysis()
        inputJob = viewModelScope.launch { analyzeInput(input) }
    }

    private fun beginAnalysis() {
        _uiState.update {
            it.copy(
                isAnalyzing = true,
                inputError = null,
                selectedSession = null,
                operationError = null,
                logcatReadError = null,
            )
        }
    }

    private suspend fun analyzeInput(
        input: String,
        saveSession: suspend (DiagnosticAnalysis) -> DiagnosticSession = historyRepository::save,
    ) {
        val revision = inputRevision
        try {
            val result = withContext(analysisDispatcher) { analyzeDiagnostic(input) }
            currentCoroutineContext().ensureActive()
            when (result) {
                is DiagnosticResult.InvalidInput -> _uiState.update { it.copy(inputError = result.reason) }
                is DiagnosticResult.Success -> {
                    val session = try {
                        saveSession(result.analysis)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        currentCoroutineContext().ensureActive()
                        _uiState.update { it.copy(operationError = HomeOperationError.HISTORY_SAVE) }
                        DiagnosticSession("unsaved-${UUID.randomUUID()}", result.analysis)
                    }
                    currentCoroutineContext().ensureActive()
                    _uiState.update { it.copy(selectedSession = session) }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            _uiState.update { it.copy(operationError = HomeOperationError.ANALYSIS) }
        } finally {
            if (inputRevision == revision) {
                _uiState.update { it.copy(isAnalyzing = false) }
            }
        }
    }

    fun openSession(id: String) {
        val session = _uiState.value.recentSessions.firstOrNull { it.id == id } ?: return
        cancelInputOperation()
        _uiState.update {
            it.copy(
                input = session.analysis.input.text,
                selectedSession = session,
                inputError = null,
                operationError = null,
                isAnalyzing = false,
                logcatReadInProgress = null,
                logcatReadError = null,
            )
        }
    }

    fun clearHistory() = removeFromHistory(sessionId = null)

    fun deleteHistorySession(sessionId: String) {
        if (_uiState.value.recentSessions.any { it.id == sessionId }) removeFromHistory(sessionId)
    }

    private fun removeFromHistory(sessionId: String?) {
        if (_uiState.value.isHistoryMutating || _uiState.value.isInputBusy) return
        _uiState.update {
            it.copy(
                isClearingHistory = sessionId == null,
                deletingSessionId = sessionId,
                operationError = it.operationError.takeUnless { error ->
                    error == HomeOperationError.HISTORY_CLEAR || error == HomeOperationError.HISTORY_DELETE
                },
            )
        }
        viewModelScope.launch {
            try {
                if (sessionId == null) historyRepository.clear() else historyRepository.delete(sessionId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val error = if (sessionId == null) HomeOperationError.HISTORY_CLEAR else HomeOperationError.HISTORY_DELETE
                _uiState.update { it.copy(operationError = error) }
            } finally {
                _uiState.update { it.copy(isClearingHistory = false, deletingSessionId = null) }
            }
        }
    }

    fun showAbout() {
        _uiState.update { it.copy(isAboutVisible = true) }
    }

    fun dismissAbout() {
        _uiState.update { it.copy(isAboutVisible = false) }
    }

    private fun cancelInputOperation() {
        inputRevision++
        inputJob?.cancel()
        inputJob = null
    }
}
