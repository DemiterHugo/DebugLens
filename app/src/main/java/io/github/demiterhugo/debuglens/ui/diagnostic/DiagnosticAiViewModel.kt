package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.repository.AiModelInstallResult
import io.github.demiterhugo.debuglens.domain.repository.AiModelRepository
import io.github.demiterhugo.debuglens.domain.repository.AiModelStatus
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticAiResult
import io.github.demiterhugo.debuglens.domain.usecase.GenerateDiagnosticAdviceUseCase
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class DiagnosticAiUiState(
    val sessionId: String? = null,
    val model: AiModelStatus? = null,
    val changingModel: Boolean = false,
    val modelError: AiModelInstallResult? = null,
    val generating: Boolean = false,
    val advice: String? = null,
    val generationError: DiagnosticAiResult.Failure? = null,
)

@HiltViewModel
internal class DiagnosticAiViewModel @Inject constructor(
    private val models: AiModelRepository,
    private val generateAdvice: GenerateDiagnosticAdviceUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(DiagnosticAiUiState())
    val state = _state.asStateFlow()
    private var generationJob: Job? = null
    private var revision = 0L

    init {
        viewModelScope.launch {
            val status = models.modelStatus()
            _state.update { it.copy(model = status) }
        }
    }

    fun selectSession(id: String?) {
        if (id == _state.value.sessionId) return
        cancelGeneration()
        _state.update { it.copy(sessionId = id, advice = null, generationError = null) }
    }

    fun installModel(documentId: String) = changeModel {
        models.installModel(documentId).takeUnless { it == AiModelInstallResult.INSTALLED }
    }

    fun removeModel() = changeModel {
        if (models.removeModel()) null else AiModelInstallResult.UNREADABLE
    }

    private fun changeModel(operation: suspend () -> AiModelInstallResult?) {
        val state = _state.value
        if (state.model == null || state.model == AiModelStatus.UNSUPPORTED || state.changingModel || state.generating) return
        _state.update { it.copy(changingModel = true, modelError = null) }
        viewModelScope.launch {
            try {
                val error = operation()
                if (error == null) {
                    val status = models.modelStatus()
                    _state.update { it.copy(model = status, advice = null, generationError = null) }
                } else {
                    _state.update { it.copy(modelError = error) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.update { it.copy(modelError = AiModelInstallResult.UNREADABLE) }
            } finally {
                _state.update { it.copy(changingModel = false) }
            }
        }
    }

    fun generate(session: DiagnosticSession) {
        val state = _state.value
        if (state.sessionId != session.id || state.model != AiModelStatus.READY || state.generating || state.changingModel) return
        val request = ++revision
        _state.update { it.copy(generating = true, generationError = null) }
        generationJob = viewModelScope.launch {
            try {
                val result = generateAdvice(session.analysis)
                ensureActive()
                when (result) {
                    is DiagnosticAiResult.Advice -> _state.update { it.copy(advice = result.text) }
                    is DiagnosticAiResult.Failure -> _state.update { it.copy(generationError = result) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                _state.update { it.copy(generationError = DiagnosticAiResult.Failure.GENERATION_FAILED) }
            } finally {
                if (request == revision) _state.update { it.copy(generating = false) }
            }
        }
    }

    fun cancelGeneration() {
        revision++
        generationJob?.cancel()
        _state.update { it.copy(generating = false) }
    }
}
