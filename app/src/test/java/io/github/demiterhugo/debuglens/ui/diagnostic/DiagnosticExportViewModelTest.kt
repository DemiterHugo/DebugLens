package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.lifecycle.SavedStateHandle
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticReportWriter
import io.github.demiterhugo.debuglens.ui.home.MainDispatcherRule
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticExportViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun writesTheSnapshotRestoredWithThePendingPickerRequest() = runTest {
        val writes = mutableListOf<Pair<String, String>>()
        val writer = DiagnosticReportWriter { id, report -> writes += id to report }
        val pending = SavedStateHandle()
        val original = DiagnosticExportViewModel(writer, pending)
        assertTrue(original.prepare("original report"))
        assertFalse(original.prepare("later selection"))

        val restored = SavedStateHandle(pending.keys().associateWith { pending.get<Any>(it) })
        val model = DiagnosticExportViewModel(writer, restored)
        assertEquals(DiagnosticExportStatus.PICKING_DESTINATION, model.status.value)
        model.onDestinationSelected("content://test/report")
        model.onDestinationSelected("content://test/duplicate")
        advanceUntilIdle()

        assertEquals(listOf("content://test/report" to "original report"), writes)
        assertEquals(DiagnosticExportStatus.SAVED, model.status.value)
        assertTrue(restored.keys().isEmpty())
    }

    @Test
    fun cancelingThePickerClearsThePendingSnapshot() = runTest {
        val savedState = SavedStateHandle()
        val model = DiagnosticExportViewModel(DiagnosticReportWriter { _, _ -> error("Unexpected write") }, savedState)
        assertTrue(model.prepare("pending report"))

        model.onDestinationSelected(null)
        advanceUntilIdle()

        assertTrue(savedState.keys().isEmpty())
        assertEquals(DiagnosticExportStatus.IDLE, model.status.value)
    }

    @Test
    fun aFailedWriteReportsAnErrorAndAllowsANewExport() = runTest {
        var fail = true
        val model = DiagnosticExportViewModel(DiagnosticReportWriter { _, _ ->
            if (fail) throw IOException("Write failed")
        }, SavedStateHandle())
        model.prepare("report")
        model.onDestinationSelected("content://test/unavailable")
        advanceUntilIdle()
        assertEquals(DiagnosticExportStatus.FAILED, model.status.value)

        fail = false
        assertTrue(model.prepare("report"))
        model.onDestinationSelected("content://test/available")
        advanceUntilIdle()
        assertEquals(DiagnosticExportStatus.SAVED, model.status.value)
    }

    @Test
    fun cancelingABlockedWriteAllowsRetryAndIgnoresTheLateResult() = runTest {
        val writes = mutableListOf<String>()
        val writer = DiagnosticReportWriter { _, report ->
            writes += report
            if (report == "blocked") {
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    // Simulate a writer that returns despite cancellation.
                }
            }
        }
        val model = DiagnosticExportViewModel(writer, SavedStateHandle())
        model.prepare("blocked")
        model.onDestinationSelected("content://test/blocked")
        runCurrent()
        assertEquals(DiagnosticExportStatus.WRITING, model.status.value)

        model.cancelWrite()
        assertEquals(DiagnosticExportStatus.CANCELLED, model.status.value)
        assertTrue(model.prepare("retry"))
        model.onDestinationSelected("content://test/retry")
        advanceUntilIdle()

        assertEquals(listOf("blocked", "retry"), writes)
        assertEquals(DiagnosticExportStatus.SAVED, model.status.value)
    }
}
