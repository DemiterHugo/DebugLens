package io.github.demiterhugo.debuglens.domain.usecase

import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class DiagnosticHistoryFilter(
    val query: String = "",
    val category: DiagnosticCategory? = null,
) {
    val isActive: Boolean get() = query.isNotBlank() || category != null
}

/** Searches a snapshot of the bounded history without changing its order. */
class DiagnosticHistorySearch(private val sessions: List<DiagnosticSession>) {
    private val searchableInputs = sessions.map { normalize(it.analysis.input.text) }

    suspend fun find(filter: DiagnosticHistoryFilter): List<DiagnosticSession> {
        if (!filter.isActive) return sessions
        val context = currentCoroutineContext()
        val terms = normalize(filter.query).split(whitespace).filter(String::isNotEmpty).distinct()
        return sessions.filterIndexed { index, session ->
            context.ensureActive()
            (filter.category == null || session.analysis.category == filter.category) &&
                terms.all { term ->
                    context.ensureActive()
                    term in searchableInputs[index]
                }
        }
    }

    private companion object {
        val whitespace = Regex("[\\s\\p{Z}]+")
        val combiningMarks = Regex("\\p{M}+")

        fun normalize(text: String): String =
            Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(combiningMarks, "")
                .replace('ς', 'σ')
    }
}
