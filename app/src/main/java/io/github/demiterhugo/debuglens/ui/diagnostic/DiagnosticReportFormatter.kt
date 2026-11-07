package io.github.demiterhugo.debuglens.ui.diagnostic

import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis

internal data class DiagnosticReportLabels(
    val appName: String,
    val causes: String,
    val anr: String,
    val trace: String,
)

internal fun formatDiagnosticReport(
    analysis: DiagnosticAnalysis,
    copy: DiagnosticPresentationText,
    labels: DiagnosticReportLabels,
    includeTrace: Boolean,
    includeAdvice: Boolean,
): String = buildString {
    appendLine(labels.appName)
    appendLine(copy.label)
    appendLine(copy.title)
    appendLine(copy.limitations)

    analysis.location?.let { location ->
        appendLine()
        appendLine(copy.location)
        appendLine(location.symbol)
        location.fileName?.let { file ->
            appendLine(location.lineNumber?.let { "$file:$it" } ?: file)
        }
    }
    if (analysis.causes.isNotEmpty()) {
        appendLine()
        appendLine(labels.causes)
        for (cause in analysis.causes) {
            appendLine("- ${cause.type}")
            cause.message?.let { appendLine("  $it") }
        }
    }
    analysis.anrEvidence?.let {
        appendLine()
        appendLine(labels.anr)
        appendLine(it)
    }
    appendLine()
    appendLine(copy.guidanceTitle)
    appendLine(copy.guidance)
    appendLine()
    appendLine(copy.checklistTitle)
    copy.checklist.forEach { appendLine("- $it") }

    if (includeAdvice) {
        copy.ai?.let {
            appendLine()
            appendLine(it.title)
            appendLine(it.advice)
            appendLine(it.note)
        }
    }
    if (includeTrace) {
        appendLine()
        appendLine(labels.trace)
        appendLine(analysis.input.text)
    }
}
