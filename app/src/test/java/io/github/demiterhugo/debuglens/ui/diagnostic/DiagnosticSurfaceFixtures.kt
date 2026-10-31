package io.github.demiterhugo.debuglens.ui.diagnostic

import io.github.demiterhugo.debuglens.domain.model.DiagnosticResult
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.usecase.AnalyzeDiagnosticUseCase

internal fun diagnosticSession(
    id: String = "session-1",
    text: String = "java.lang.RuntimeException: wrapper\n" +
        "Caused by: java.lang.NullPointerException: missing value\n" +
        "\tat example.Profile.render(Profile.kt:42)",
) = DiagnosticSession(id, (AnalyzeDiagnosticUseCase()(text) as DiagnosticResult.Success).analysis)

internal fun diagnosticCopy() = DiagnosticSurfaceCopy(
    label = "Local analysis", title = "Null reference", limitations = "Check the code",
    location = "Location", guidanceTitle = "Review", guidance = "Inspect the value",
    checklistTitle = "Checklist", checklist = listOf("Inspect value", "Read trace", "Reproduce"),
    showCauses = "Show causes", hideCauses = "Hide causes", causesTitle = "Cause chain",
    causesOmitted = "Only the deepest causes are shown", showSource = "Show source", hideSource = "Hide source",
    sourceOmitted = "Source preview truncated",
)
