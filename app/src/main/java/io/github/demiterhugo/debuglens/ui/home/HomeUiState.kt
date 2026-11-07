package io.github.demiterhugo.debuglens.ui.home

import io.github.demiterhugo.debuglens.domain.model.DiagnosticInputError
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.repository.LogcatImportResult

data class HomeUiState(
    val input: String = "",
    val inputError: DiagnosticInputError? = null,
    val isAnalyzing: Boolean = false,
    val isImportingLogcat: Boolean = false,
    val logcatImportError: LogcatImportResult.Failure? = null,
    val selectedSession: DiagnosticSession? = null,
    val recentSessions: List<DiagnosticSession> = emptyList(),
    val isHistoryLoading: Boolean = true,
    val historyLoadError: Boolean = false,
    val isClearingHistory: Boolean = false,
    val operationError: HomeOperationError? = null,
    val isAboutVisible: Boolean = false,
) {
    val analysis get() = selectedSession?.analysis
    val isInputBusy get() = isAnalyzing || isImportingLogcat
}

enum class HomeOperationError {
    ANALYSIS,
    HISTORY_SAVE,
    HISTORY_CLEAR,
}
