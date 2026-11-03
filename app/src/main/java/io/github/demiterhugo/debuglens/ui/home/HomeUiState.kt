package io.github.demiterhugo.debuglens.ui.home

import io.github.demiterhugo.debuglens.domain.model.DiagnosticInputError
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession

data class HomeUiState(
    val input: String = "",
    val inputError: DiagnosticInputError? = null,
    val isAnalyzing: Boolean = false,
    val selectedSession: DiagnosticSession? = null,
    val recentSessions: List<DiagnosticSession> = emptyList(),
    val isHistoryLoading: Boolean = true,
    val historyLoadError: Boolean = false,
    val isClearingHistory: Boolean = false,
    val operationError: HomeOperationError? = null,
    val isAboutVisible: Boolean = false,
) {
    val analysis get() = selectedSession?.analysis
}

enum class HomeOperationError {
    ANALYSIS,
    HISTORY_SAVE,
    HISTORY_CLEAR,
}
