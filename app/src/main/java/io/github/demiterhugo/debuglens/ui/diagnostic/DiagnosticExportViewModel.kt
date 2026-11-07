package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticReportWriter
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal enum class DiagnosticExportStatus {
    IDLE,
    PICKING_DESTINATION,
    WRITING,
    SAVED,
    CANCELLED,
    FAILED;

    val isBusy get() = this == PICKING_DESTINATION || this == WRITING
}

@HiltViewModel
internal class DiagnosticExportViewModel @Inject constructor(
    private val writer: DiagnosticReportWriter,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val _status = MutableStateFlow(
        if (savedState.contains(PENDING_REPORT)) DiagnosticExportStatus.PICKING_DESTINATION
        else DiagnosticExportStatus.IDLE,
    )
    val status = _status.asStateFlow()
    private var writeJob: Job? = null

    fun prepare(report: String): Boolean {
        if (_status.value.isBusy) return false
        savedState[PENDING_REPORT] = report
        _status.value = DiagnosticExportStatus.PICKING_DESTINATION
        return true
    }

    fun onDestinationSelected(folderId: String?) {
        if (_status.value != DiagnosticExportStatus.PICKING_DESTINATION) {
            if (_status.value == DiagnosticExportStatus.IDLE && folderId != null) {
                _status.value = DiagnosticExportStatus.FAILED
            }
            return
        }
        val report = savedState.remove<String>(PENDING_REPORT)
        if (folderId == null) {
            _status.value = DiagnosticExportStatus.IDLE
            return
        }
        if (report == null) {
            _status.value = DiagnosticExportStatus.FAILED
            return
        }
        _status.value = DiagnosticExportStatus.WRITING
        writeJob = viewModelScope.launch {
            try {
                writer.writeNew(folderId, report)
                ensureActive()
                _status.value = DiagnosticExportStatus.SAVED
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ensureActive()
                _status.value = DiagnosticExportStatus.FAILED
            }
        }
    }

    fun cancelWrite() {
        if (_status.value != DiagnosticExportStatus.WRITING) return
        writeJob?.cancel()
        _status.value = DiagnosticExportStatus.CANCELLED
    }

    fun onPickerUnavailable() {
        savedState.remove<String>(PENDING_REPORT)
        _status.value = DiagnosticExportStatus.FAILED
    }

    fun dismissResult() {
        if (!_status.value.isBusy) _status.value = DiagnosticExportStatus.IDLE
    }

    private companion object {
        const val PENDING_REPORT = "pending_diagnostic_report"
    }
}
