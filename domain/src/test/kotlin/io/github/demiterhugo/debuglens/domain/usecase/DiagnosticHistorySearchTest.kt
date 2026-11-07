package io.github.demiterhugo.debuglens.domain.usecase

import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.model.DiagnosticResult
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticHistorySearchTest {
    private val newest = session(
        "new", "java.lang.IllegalStateException: sesión caducada\n\tat sample.Café.render(Profile.kt:42)",
    )
    private val previous = session(
        "old", "java.lang.NullPointerException: sesión vacía\n\tat sample.Café.read(Account.kt:7)",
    )
    private val sessions = listOf(newest, previous)
    private val search = DiagnosticHistorySearch(sessions)

    @Test
    fun matchesAllTermsAcrossTheTraceRegardlessOfCaseAndAccents() = runBlocking {
        assertEquals(listOf(newest), search.find(DiagnosticHistoryFilter("  CAFE\tprofile.KT:42\nSESION ")))
        assertEquals(emptyList<DiagnosticSession>(), search.find(DiagnosticHistoryFilter("cafe missing")))
    }

    @Test
    fun combinesTextAndCategoryAndAllowsCategoryOnly() = runBlocking {
        val category = DiagnosticCategory.NULL_REFERENCE
        assertEquals(listOf(previous), search.find(DiagnosticHistoryFilter("sesion", category)))
        assertEquals(listOf(previous), search.find(DiagnosticHistoryFilter(category = category)))
        assertEquals(emptyList<DiagnosticSession>(), search.find(DiagnosticHistoryFilter("Profile.kt", category)))
    }

    @Test
    fun preservesTheHistoryOrderAndTreatsWhitespaceAsAnEmptyQuery() = runBlocking {
        assertEquals(sessions, search.find(DiagnosticHistoryFilter(" \t\n")))
        assertEquals(sessions, search.find(DiagnosticHistoryFilter("SESION")))
    }

    @Test
    fun matchesUppercaseAndBothLowercaseFormsOfGreekSigma() = runBlocking {
        val uppercase = session("uppercase", "IllegalStateException: ΟΣ")
        val lowercase = session("lowercase", "IllegalStateException: ος")
        val matches = listOf(uppercase, lowercase)
        val greekSearch = DiagnosticHistorySearch(matches)

        for (query in listOf("Σ", "σ", "ς", "ΟΣ", "οσ", "ος")) {
            assertEquals(query, matches, greekSearch.find(DiagnosticHistoryFilter(query)))
        }
    }

    private fun session(id: String, trace: String): DiagnosticSession =
        DiagnosticSession(id, (AnalyzeDiagnosticUseCase()(trace) as DiagnosticResult.Success).analysis)
}
