package io.github.demiterhugo.debuglens.domain.model

@JvmInline
value class DiagnosticInput private constructor(val text: String) {
    companion object {
        const val MAX_LENGTH = 65_536

        /** The limit applies to the original input, before normalization. */
        fun parse(rawText: String): DiagnosticInputResult {
            if (rawText.length > MAX_LENGTH) {
                return DiagnosticInputResult.Invalid(DiagnosticInputError.TOO_LONG)
            }

            val normalized = rawText.replace("\r\n", "\n").replace('\r', '\n').trim()
            if (normalized.isEmpty()) {
                return DiagnosticInputResult.Invalid(DiagnosticInputError.EMPTY)
            }

            return DiagnosticInputResult.Valid(DiagnosticInput(normalized))
        }
    }
}

enum class DiagnosticInputError {
    EMPTY,
    TOO_LONG,
}

sealed interface DiagnosticInputResult {
    data class Valid(val input: DiagnosticInput) : DiagnosticInputResult
    data class Invalid(val reason: DiagnosticInputError) : DiagnosticInputResult
}
