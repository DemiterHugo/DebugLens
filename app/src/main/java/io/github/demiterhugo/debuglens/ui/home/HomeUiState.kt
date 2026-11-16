package io.github.demiterhugo.debuglens.ui.home

import io.github.demiterhugo.debuglens.domain.model.DiagnosticInputError
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.repository.LogcatIncidentResult
import io.github.demiterhugo.debuglens.domain.usecase.DiagnosticHistoryFilter

data class HomeUiState(
    val input: String = "",
    val inputError: DiagnosticInputError? = null,
    val isAnalyzing: Boolean = false,
    val logcatReadInProgress: LogcatSource? = null,
    val logcatReadError: LogcatReadError? = null,
    val selectedSession: DiagnosticSession? = null,
    val surfaceRevision: Long = 0L,
    val recentSessions: List<DiagnosticSession> = emptyList(),
    val matchingSessions: List<DiagnosticSession> = emptyList(),
    val historyFilter: DiagnosticHistoryFilter = DiagnosticHistoryFilter(),
    val isHistoryLoading: Boolean = true,
    val isHistorySearching: Boolean = false,
    val historyLoadError: Boolean = false,
    val isClearingHistory: Boolean = false,
    val deletingSessionId: String? = null,
    val operationError: HomeOperationError? = null,
    val isAboutVisible: Boolean = false,
) {
    val analysis get() = selectedSession?.analysis
    val isInputBusy get() = isAnalyzing || logcatReadInProgress != null
    val isHistoryMutating get() = isClearingHistory || deletingSessionId != null
}

enum class LogcatSource { DOCUMENT, DEVICE }

data class LogcatReadError(val source: LogcatSource, val reason: LogcatIncidentResult.Failure)

enum class HomeOperationError {
    ANALYSIS,
    HISTORY_SAVE,
    HISTORY_CLEAR,
    HISTORY_DELETE,
}
