package io.github.demiterhugo.debuglens.data.local

import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.model.DiagnosticResult
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.model.StackFrame
import io.github.demiterhugo.debuglens.domain.usecase.AnalyzeDiagnosticUseCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class DiagnosticHistoryMapperTest {
    private fun session(text: String = SAMPLE): DiagnosticSession = DiagnosticSession(
        "stable-session-id", (AnalyzeDiagnosticUseCase()(text) as DiagnosticResult.Success).analysis,
    )

    @Test
    fun roundTripsIdentityEvidenceCauseChainAndLocation() {
        val original = session()
        assertEquals(original, original.toStored().toDomain())
    }

    @Test
    fun restoresCauseOrderIndependentlyOfDatabaseRowOrder() {
        val original = session()
        val stored = original.toStored()
        assertEquals(original, stored.copy(causes = stored.causes.reversed()).toDomain())
    }

    @Test
    fun roundTripsEveryKnownCategoryWithoutReanalyzingTheStoredResult() {
        val base = session()
        DiagnosticCategory.entries.forEach { category ->
            val original = base.copy(analysis = base.analysis.copy(category = category))
            assertEquals(original, original.toStored().toDomain())
        }
    }

    @Test
    fun preservesMissingMessageAndLocation() {
        val original = session("java.lang.IllegalStateException")
        val restored = original.toStored().toDomain()
        assertEquals(original, restored)
        assertNull(restored.analysis.rootCause?.message)
        assertNull(restored.analysis.location)
    }

    @Test
    fun preservesAnrAndUnclassifiedFragments() {
        listOf("ANR in com.example.app", "A blank screen without an exception").forEach { input ->
            val original = session(input)
            assertEquals(original, original.toStored().toDomain())
        }
    }

    @Test
    fun preservesLocationWithUnknownSourceAndLine() {
        val base = session()
        val original = base.copy(analysis = base.analysis.copy(location = StackFrame("native.render", null, null)))
        assertEquals(original, original.toStored().toDomain())
    }

    @Test
    fun unknownStoredCategoriesRetainEvidenceWithAnUnknownClassification() {
        val original = session()
        val stored = original.toStored()
        val restored = stored.copy(session = stored.session.copy(category = "FUTURE_PATTERN")).toDomain()
        assertEquals(original.copy(analysis = original.analysis.copy(category = DiagnosticCategory.UNKNOWN)), restored)
    }

    @Test
    fun rejectsInvalidPersistedInputRatherThanInventingAnAnalysis() {
        val stored = session().toStored()
        listOf(" ", "x".repeat(65_537)).forEach { input ->
            assertThrows(IllegalStateException::class.java) { stored.copy(session = stored.session.copy(input = input)).toDomain() }
        }
    }

    companion object {
        private const val SAMPLE = "java.lang.RuntimeException: wrapper\n" +
            "Caused by: java.lang.NullPointerException: \"missing\" 😃\n" +
            "\tat sample.Profile.render(Profile.kt:42)"
    }
}
