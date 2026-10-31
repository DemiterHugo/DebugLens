package io.github.demiterhugo.debuglens.domain.usecase

import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis
import io.github.demiterhugo.debuglens.domain.model.DiagnosticCategory
import io.github.demiterhugo.debuglens.domain.model.DiagnosticInputError
import io.github.demiterhugo.debuglens.domain.model.DiagnosticResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnalyzeDiagnosticUseCaseTest {
    private val analyze = AnalyzeDiagnosticUseCase()

    @Test
    fun returnsValidationErrorsWithoutProducingAnAnalysis() {
        assertEquals(DiagnosticResult.InvalidInput(DiagnosticInputError.EMPTY), analyze("  "))
    }

    @Test
    fun classifiesSupportedExceptionTypes() {
        val cases = mapOf(
            "NullPointerException" to DiagnosticCategory.NULL_REFERENCE,
            "IllegalStateException" to DiagnosticCategory.INVALID_STATE,
            "IllegalArgumentException" to DiagnosticCategory.INVALID_ARGUMENT,
            "IndexOutOfBoundsException" to DiagnosticCategory.INDEX_OUT_OF_BOUNDS,
            "ArrayIndexOutOfBoundsException" to DiagnosticCategory.INDEX_OUT_OF_BOUNDS,
            "StringIndexOutOfBoundsException" to DiagnosticCategory.INDEX_OUT_OF_BOUNDS,
            "ClassCastException" to DiagnosticCategory.TYPE_MISMATCH,
            "SecurityException" to DiagnosticCategory.SECURITY,
            "OutOfMemoryError" to DiagnosticCategory.OUT_OF_MEMORY,
        )
        cases.forEach { (type, expected) ->
            assertEquals(type, expected, success("java.lang.$type: detail").category)
        }
    }

    @Test
    fun selectsTheDeepestCauseAndItsApplicationLocation() {
        val result = success(
            """
            java.lang.RuntimeException: Unable to start activity
                at android.app.ActivityThread.performLaunch(ActivityThread.java:100)
            Caused by: java.lang.IllegalStateException: Wrapper
                at example.Outer.run(Outer.kt:12)
            Caused by: java.lang.NullPointerException: missing name
                at kotlin.collections.CollectionsKt.first(Collections.kt:10)
                at example.Profile.render(Profile.kt:42)
            """.trimIndent(),
        )

        assertEquals(3, result.causes.size)
        assertEquals(DiagnosticCategory.NULL_REFERENCE, result.category)
        assertEquals("missing name", result.rootCause?.message)
        assertEquals("example.Profile.render", result.location?.symbol)
        assertEquals("Profile.kt", result.location?.fileName)
        assertEquals(42, result.location?.lineNumber)
    }

    @Test
    fun ignoresSuppressedExceptionsAndTheirNestedCauses() {
        val result = success(
            "java.lang.IllegalStateException: primary\n" +
                "\tat example.Main.run(Main.kt:7)\n" +
                "\tSuppressed: java.lang.RuntimeException: close failed\n" +
                "\t\tat example.Close.run(Close.kt:8)\n" +
                "\tCaused by: java.lang.NullPointerException: suppressed cause\n" +
                "\t\tat example.Close.fail(Close.kt:9)",
        )

        assertEquals(DiagnosticCategory.INVALID_STATE, result.category)
        assertEquals(1, result.causes.size)
        assertEquals("Main.kt", result.location?.fileName)
    }

    @Test
    fun resumesTheMainCauseChainAfterASuppressedBranch() {
        val result = success(
            "java.lang.RuntimeException: wrapper\n" +
                "\tSuppressed: java.lang.IllegalStateException: suppressed\n" +
                "\tCaused by: java.lang.SecurityException: suppressed cause\n" +
                "Caused by: java.lang.IllegalArgumentException: actual cause\n" +
                "\tat example.Main.run(Main.kt:10)",
        )

        assertEquals(DiagnosticCategory.INVALID_ARGUMENT, result.category)
        assertEquals(2, result.causes.size)
        assertEquals(10, result.location?.lineNumber)
    }

    @Test
    fun supportsCommonPrefixesInManuallyPastedLogs() {
        val prefixes = listOf(
            "10-31 12:34:56.123  1234  1234 E AndroidRuntime: ",
            "2026-10-31 12:34:56.123 1234-1234 AndroidRuntime example.app E  ",
            "E/AndroidRuntime( 1234): ",
            "E AndroidRuntime: ",
            "AndroidRuntime: ",
        )
        prefixes.forEach { prefix ->
            val result = success(
                "${prefix}java.lang.RuntimeException: wrapper\n" +
                    "${prefix}Caused by: java.lang.NullPointerException: missing\n" +
                    "${prefix}\tat example.Main.run(Main.kt:42)",
            )
            assertEquals(prefix, DiagnosticCategory.NULL_REFERENCE, result.category)
            assertEquals(prefix, 42, result.location?.lineNumber)
        }
    }

    @Test
    fun acceptsAThreadHeaderAndAnExceptionWithoutAMessage() {
        val result = success("Exception in thread \"main\" java.lang.NullPointerException")

        assertEquals(DiagnosticCategory.NULL_REFERENCE, result.category)
        assertNull(result.rootCause?.message)
    }

    @Test
    fun doesNotClassifyWordsInsideAnExceptionMessage() {
        val result = success("java.lang.IllegalStateException: NullPointerException and OutOfMemoryError")

        assertEquals(DiagnosticCategory.INVALID_STATE, result.category)
    }

    @Test
    fun doesNotTreatAnArbitraryMentionAsAnExceptionHeader() {
        val result = success("El mensaje menciona NullPointerException pero falta una traza")

        assertEquals(DiagnosticCategory.UNKNOWN, result.category)
        assertNull(result.rootCause)
    }

    @Test
    fun leavesUnknownExceptionsUnclassifiedAndKeepsTheirEvidence() {
        val result = success("example.CustomException: custom problem")

        assertEquals(DiagnosticCategory.UNKNOWN, result.category)
        assertEquals("example.CustomException", result.rootCause?.type)
    }

    @Test
    fun recognizesExplicitAnrMarkers() {
        listOf("ANR in example.app", "Reason: Input dispatching timed out (window)").forEach { text ->
            val result = success(text)
            assertEquals(DiagnosticCategory.UNRESPONSIVE, result.category)
            assertEquals(text, result.anrEvidence)
        }
    }

    @Test
    fun anrWordsInsideAnExceptionMessageDoNotOverrideTheException() {
        assertEquals(
            DiagnosticCategory.INVALID_STATE,
            success("java.lang.IllegalStateException: Input dispatching timed out").category,
        )
    }

    @Test
    fun doesNotMergeIndependentExceptionTraces() {
        val result = success(
            "java.lang.IllegalArgumentException: first\n" +
                "\tat example.First.run(First.kt:1)\n" +
                "java.lang.NullPointerException: second\n" +
                "\tat example.Second.run(Second.kt:2)",
        )

        assertEquals(DiagnosticCategory.INVALID_ARGUMENT, result.category)
        assertEquals("First.kt", result.location?.fileName)
    }

    @Test
    fun handlesUnavailableLocationsAndOverflowingLineNumbers() {
        val result = success(
            "java.lang.NullPointerException\n" +
                "\tat example.First.run(Unknown Source)\n" +
                "\tat example.Second.run(Second.kt:999999999999999999999)",
        )

        assertEquals("example.First.run", result.location?.symbol)
        assertNull(result.location?.lineNumber)
    }

    @Test
    fun acceptsBaseExceptionAndErrorTypes() {
        assertEquals("java.lang.Exception", success("java.lang.Exception: detail").rootCause?.type)
        assertEquals("java.lang.Error", success("java.lang.Error: detail").rootCause?.type)
    }

    @Test
    fun handlesDeeplyQualifiedTypesWithoutRecursiveRegexMatching() {
        val type = "segment.".repeat(4_000) + "CustomException"

        assertEquals(type, success("$type: detail").rootCause?.type)
    }

    @Test
    fun rejectsMalformedQualifiedExceptionNames() {
        val result = success("example..NullPointerException: malformed class")

        assertEquals(DiagnosticCategory.UNKNOWN, result.category)
        assertNull(result.rootCause)
    }

    @Test
    fun followsCausesWhenThePastedBlockHasOuterIndentation() {
        val result = success(
            "    java.lang.RuntimeException: wrapper\n" +
                "    Caused by: java.lang.NullPointerException: cause\n" +
                "        at example.Main.run(Main.kt:8)",
        )

        assertEquals(DiagnosticCategory.NULL_REFERENCE, result.category)
        assertEquals(8, result.location?.lineNumber)
    }

    private fun success(text: String): DiagnosticAnalysis =
        (analyze(text) as DiagnosticResult.Success).analysis
}
