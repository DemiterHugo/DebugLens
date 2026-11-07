package io.github.demiterhugo.debuglens.domain.usecase

import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.model.DiagnosticInput
import io.github.demiterhugo.debuglens.domain.model.DiagnosticResult
import io.github.demiterhugo.debuglens.domain.model.StackFrame
import io.github.demiterhugo.debuglens.domain.repository.LogcatImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ExtractLogcatIncidentUseCaseTest {
    private val extract = ExtractLogcatIncidentUseCase()
    private val analyze = AnalyzeDiagnosticUseCase()

    @Test
    fun extractsTheLastCrashWithoutMixingProcessesOrThreads() {
        val dump = """
            --------- beginning of crash
            11-07 10:11:12.100  2200  2200 E AndroidRuntime: FATAL EXCEPTION: main
            11-07 10:11:12.100  2200  2200 E AndroidRuntime: java.lang.SecurityException: earlier
            11-07 10:11:13.200  3300  3310 E AndroidRuntime: FATAL EXCEPTION: worker
            11-07 10:11:13.200  3300  3310 E AndroidRuntime: Process: sample.app, PID: 3300
            11-07 10:11:13.200  3300  3310 E AndroidRuntime: java.lang.RuntimeException: wrapper
            11-07 10:11:13.200  2200  2200 E AndroidRuntime: Caused by: java.lang.SecurityException: other process
            11-07 10:11:13.200  3300  3311 E AndroidRuntime: Caused by: java.lang.SecurityException: other thread
            11-07 10:11:13.200  3300  3310 I OtherTag: unrelated message
            11-07 10:11:13.200  3300  3310 E AndroidRuntime: Caused by: java.lang.IllegalStateException: missing state
            11-07 10:11:13.200  3300  3310 E AndroidRuntime:     at sample.Worker.run(Worker.kt:42)
        """.trimIndent().replace("\n", "\r\n")

        val incident = extract(dump) as LogcatImportResult.Imported
        val analysis = (analyze(incident.trace) as DiagnosticResult.Success).analysis
        assertEquals(DiagnosticCategory.INVALID_STATE, analysis.category)
        assertEquals(listOf("java.lang.RuntimeException", "java.lang.IllegalStateException"), analysis.causes.map { it.type })
        assertEquals(StackFrame("sample.Worker.run", "Worker.kt", 42), analysis.location)
        assertFalse(incident.trace.contains("unrelated message"))
    }

    @Test
    fun selectsASystemProcessCrashAfterAnApplicationCrash() {
        val dump = """
            11-07 10:11:12.100  2200  2200 E AndroidRuntime: FATAL EXCEPTION: main
            11-07 10:11:12.100  2200  2200 E AndroidRuntime: java.lang.SecurityException: earlier
            11-07 10:11:13.200  1000  1001 E AndroidRuntime: *** FATAL EXCEPTION IN SYSTEM PROCESS: android.fg
            11-07 10:11:13.200  1000  1001 E AndroidRuntime: java.lang.IllegalStateException: system service failed
            11-07 10:11:13.200  1000  1001 E AndroidRuntime:     at com.android.server.Example.run(Example.java:42)
        """.trimIndent()

        val incident = extract(dump) as LogcatImportResult.Imported
        val analysis = (analyze(incident.trace) as DiagnosticResult.Success).analysis
        assertEquals(DiagnosticCategory.INVALID_STATE, analysis.category)
        assertEquals("system service failed", analysis.rootCause?.message)
        assertEquals(StackFrame("com.android.server.Example.run", "Example.java", 42), analysis.location)
        assertFalse(incident.trace.contains("earlier"))
    }

    @Test
    fun selectsANewerAnrInsteadOfAnEarlierCrash() {
        val dump = """
            2026-11-07 10:11:12.100  2200  2200 E AndroidRuntime: FATAL EXCEPTION: main
            2026-11-07 10:11:12.100  2200  2200 E AndroidRuntime: java.lang.SecurityException: earlier
            2026-11-07 10:11:13.200  1000  1010 E ActivityManager: ANR in sample.app
            2026-11-07 10:11:13.200  1000  1010 E ActivityManager: Reason: Input dispatching timed out
        """.trimIndent()

        val incident = extract(dump) as LogcatImportResult.Imported
        val analysis = (analyze(incident.trace) as DiagnosticResult.Success).analysis
        assertEquals(DiagnosticCategory.UNRESPONSIVE, analysis.category)
        assertEquals("ANR in sample.app", analysis.anrEvidence)
    }

    @Test
    fun ignoresMessagesThatOnlyMentionCrashMarkers() {
        val dump = """
            11-07 10:11:12.100  2200  2200 I AndroidRuntime: FATAL EXCEPTION: main
            11-07 10:11:12.100  2200  2200 E SampleTag: ANR in sample.app
            java.lang.IllegalStateException: no Logcat metadata
        """.trimIndent()
        assertEquals(LogcatImportResult.Failure.NO_INCIDENT, extract(dump))
    }

    @Test
    fun rejectsAnOversizeIncidentWithoutBlockingALaterOne() {
        val header = "11-07 10:11:12.100  2200  2200 E AndroidRuntime: FATAL EXCEPTION: main"
        val longRecord = "11-07 10:11:12.100  2200  2200 E AndroidRuntime: " +
            "x".repeat(DiagnosticInput.MAX_LENGTH)
        val oversized = "$header\n$longRecord"
        assertEquals(LogcatImportResult.Failure.INCIDENT_TOO_LONG, extract(oversized))

        val later = "11-07 10:11:13.200  1000  1010 E ActivityManager: ANR in sample.app"
        assertEquals(LogcatImportResult.Imported(later), extract("$oversized\n$later"))
    }
}
