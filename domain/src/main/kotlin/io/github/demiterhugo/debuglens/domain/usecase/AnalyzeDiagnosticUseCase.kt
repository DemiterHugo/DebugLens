package io.github.demiterhugo.debuglens.domain.usecase

import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis
import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.model.DiagnosticInput
import io.github.demiterhugo.debuglens.domain.model.DiagnosticInputResult
import io.github.demiterhugo.debuglens.domain.model.DiagnosticResult

class AnalyzeDiagnosticUseCase {
    private val parser = StackTraceParser()

    operator fun invoke(rawText: String): DiagnosticResult {
        val input = when (val result = DiagnosticInput.parse(rawText)) {
            is DiagnosticInputResult.Invalid -> return DiagnosticResult.InvalidInput(result.reason)
            is DiagnosticInputResult.Valid -> result.input
        }
        val trace = parser.parse(input)
        val category = when (trace.causes.lastOrNull()?.type?.substringAfterLast('.')) {
            "NullPointerException" -> DiagnosticCategory.NULL_REFERENCE
            "IllegalStateException" -> DiagnosticCategory.INVALID_STATE
            "IllegalArgumentException" -> DiagnosticCategory.INVALID_ARGUMENT
            "IndexOutOfBoundsException", "ArrayIndexOutOfBoundsException", "StringIndexOutOfBoundsException" ->
                DiagnosticCategory.INDEX_OUT_OF_BOUNDS
            "ClassCastException" -> DiagnosticCategory.TYPE_MISMATCH
            "SecurityException" -> DiagnosticCategory.SECURITY
            "OutOfMemoryError" -> DiagnosticCategory.OUT_OF_MEMORY
            else -> if (trace.causes.isEmpty() && trace.anrEvidence != null) {
                DiagnosticCategory.UNRESPONSIVE
            } else {
                DiagnosticCategory.UNKNOWN
            }
        }

        return DiagnosticResult.Success(
            DiagnosticAnalysis(input, category, trace.causes, trace.location, trace.anrEvidence),
        )
    }
}
