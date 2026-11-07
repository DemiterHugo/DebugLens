package io.github.demiterhugo.debuglens.domain.repository

fun interface DiagnosticAdviceGenerator {
    suspend fun generateAdvice(prompt: String): DiagnosticAiResult
}

sealed interface DiagnosticAiResult {
    data class Advice(val text: String) : DiagnosticAiResult
    enum class Failure : DiagnosticAiResult { UNAVAILABLE, GENERATION_FAILED }
}
