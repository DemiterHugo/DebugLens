package io.github.demiterhugo.debuglens.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticInputTest {
    @Test
    fun rejectsEmptyInput() {
        assertEquals(
            DiagnosticInputResult.Invalid(DiagnosticInputError.EMPTY),
            DiagnosticInput.parse(""),
        )
    }

    @Test
    fun rejectsWhitespaceOnlyInput() {
        assertEquals(
            DiagnosticInputResult.Invalid(DiagnosticInputError.EMPTY),
            DiagnosticInput.parse(" \t\r\n \u2003"),
        )
    }

    @Test
    fun normalizesLineEndingsAndPreservesInternalStackTraceIndentation() {
        val result = DiagnosticInput.parse(
            "  java.lang.IllegalStateException: Invalid state\r\n" +
                "\tat example.Main.run(Main.kt:42)\rCaused by: java.lang.Exception: detail\r\n",
        ) as DiagnosticInputResult.Valid

        assertEquals(
            "java.lang.IllegalStateException: Invalid state\n" +
                "\tat example.Main.run(Main.kt:42)\nCaused by: java.lang.Exception: detail",
            result.input.text,
        )
    }

    @Test
    fun acceptsInputAtTheLengthLimit() {
        assertTrue(DiagnosticInput.parse("x".repeat(DiagnosticInput.MAX_LENGTH)) is DiagnosticInputResult.Valid)
    }

    @Test
    fun rejectsInputOverTheLengthLimit() {
        assertEquals(
            DiagnosticInputResult.Invalid(DiagnosticInputError.TOO_LONG),
            DiagnosticInput.parse("x".repeat(DiagnosticInput.MAX_LENGTH + 1)),
        )
    }

    @Test
    fun appliesLengthLimitBeforeTrimming() {
        assertEquals(
            DiagnosticInputResult.Invalid(DiagnosticInputError.TOO_LONG),
            DiagnosticInput.parse(" ".repeat(DiagnosticInput.MAX_LENGTH) + "x"),
        )
    }

    @Test
    fun preservesTextThatIsNotAStackTrace() {
        val result = DiagnosticInput.parse("  La aplicación se ha detenido  ") as DiagnosticInputResult.Valid

        assertEquals("La aplicación se ha detenido", result.input.text)
    }
}
