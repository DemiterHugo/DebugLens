package io.github.demiterhugo.debuglens.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.demiterhugo.debuglens.di.AnalysisDispatcher
import io.github.demiterhugo.debuglens.domain.model.DiagnosticResult
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticHistoryRepository
import io.github.demiterhugo.debuglens.domain.usecase.AnalyzeDiagnosticUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val analyzeDiagnostic: AnalyzeDiagnosticUseCase,
    private val historyRepository: DiagnosticHistoryRepository,
    @param:AnalysisDispatcher private val analysisDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var analysisJob: Job? = null
    private var inputRevision = 0L

    init {
        viewModelScope.launch {
            try {
                historyRepository.observeSessions().collect { sessions ->
                    _uiState.update { it.copy(recentSessions = sessions) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(operationError = HomeOperationError.HISTORY) }
            }
        }
    }

    fun updateInput(input: String) {
        cancelAnalysis()
        _uiState.update {
            it.copy(
                input = input,
                inputError = null,
                selectedSession = null,
                operationError = null,
                isAnalyzing = false,
            )
        }
    }

    fun analyze() {
        if (_uiState.value.isAnalyzing) return
        val input = _uiState.value.input
        val revision = inputRevision
        _uiState.update {
            it.copy(isAnalyzing = true, inputError = null, selectedSession = null, operationError = null)
        }
        analysisJob = viewModelScope.launch {
            try {
                val result = withContext(analysisDispatcher) { analyzeDiagnostic(input) }
                ensureActive()
                when (result) {
                    is DiagnosticResult.InvalidInput -> _uiState.update { it.copy(inputError = result.reason) }
                    is DiagnosticResult.Success -> {
                        val session = historyRepository.save(result.analysis)
                        ensureActive()
                        _uiState.update { it.copy(selectedSession = session) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(operationError = HomeOperationError.ANALYSIS) }
            } finally {
                if (inputRevision == revision) {
                    _uiState.update { it.copy(isAnalyzing = false) }
                }
            }
        }
    }

    fun openSession(id: String) {
        val session = _uiState.value.recentSessions.firstOrNull { it.id == id } ?: return
        cancelAnalysis()
        _uiState.update {
            it.copy(
                input = session.analysis.input.text,
                selectedSession = session,
                inputError = null,
                operationError = null,
                isAnalyzing = false,
            )
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                historyRepository.clear()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(operationError = HomeOperationError.HISTORY) }
            }
        }
    }

    fun showAbout() {
        _uiState.update { it.copy(isAboutVisible = true) }
    }

    fun dismissAbout() {
        _uiState.update { it.copy(isAboutVisible = false) }
    }

    private fun cancelAnalysis() {
        inputRevision++
        analysisJob?.cancel()
        analysisJob = null
    }
}
