package io.github.demiterhugo.debuglens.ui.diagnostic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticReportFormatterTest {
    private val session = diagnosticSession(text = "unparsed marker\n" +
        "java.lang.RuntimeException: wrapper\n" +
        "Caused by: java.lang.NullPointerException: café 😀\n\tat example.Profile.render(Profile.kt:42)")
    private val copy = diagnosticText().copy(ai = AiDiagnosticCopy("Local AI", "AI advice", "Verify it"))
    private val labels = DiagnosticReportLabels("DebugLens", "Causes", "ANR evidence", "Original trace")

    @Test
    fun summaryContainsDiagnosticEvidenceAndChecksButOmitsOptionalSections() {
        val report = formatDiagnosticReport(session.analysis, copy, labels, includeTrace = false, includeAdvice = false)

        assertTrue(report.contains("Null reference"))
        assertTrue(report.contains("Profile.kt:42"))
        assertTrue(report.contains("Causes\n- java.lang.RuntimeException\n  wrapper\n- java.lang.NullPointerException\n  café 😀"))
        assertTrue(report.contains("Checklist\n- Inspect value"))
        assertFalse(report.contains("unparsed marker"))
        assertFalse(report.contains("AI advice"))
    }

    @Test
    fun optionalTraceIsCompleteAndAdviceHasItsOwnSection() {
        val report = formatDiagnosticReport(session.analysis, copy, labels, includeTrace = true, includeAdvice = true)

        assertTrue(report.contains("\nLocal AI\nAI advice\nVerify it\n"))
        assertTrue(report.endsWith("\nOriginal trace\n${session.analysis.input.text}\n"))
    }
}
