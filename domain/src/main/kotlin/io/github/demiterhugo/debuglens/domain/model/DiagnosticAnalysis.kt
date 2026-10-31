package io.github.demiterhugo.debuglens.domain.model

data class DiagnosticAnalysis(
    val input: DiagnosticInput,
    val category: DiagnosticCategory,
    val causes: List<ExceptionSummary>,
    val location: StackFrame?,
    val anrEvidence: String?,
) {
    val rootCause: ExceptionSummary?
        get() = causes.lastOrNull()
}

data class ExceptionSummary(
    val type: String,
    val message: String?,
)

data class StackFrame(
    val symbol: String,
    val fileName: String?,
    val lineNumber: Int?,
)

enum class DiagnosticCategory {
    NULL_REFERENCE,
    INVALID_STATE,
    INVALID_ARGUMENT,
    INDEX_OUT_OF_BOUNDS,
    TYPE_MISMATCH,
    SECURITY,
    OUT_OF_MEMORY,
    UNRESPONSIVE,
    UNKNOWN,
}

sealed interface DiagnosticResult {
    data class Success(val analysis: DiagnosticAnalysis) : DiagnosticResult
    data class InvalidInput(val reason: DiagnosticInputError) : DiagnosticResult
}
