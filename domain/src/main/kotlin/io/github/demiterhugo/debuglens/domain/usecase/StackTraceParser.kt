package io.github.demiterhugo.debuglens.domain.usecase

import io.github.demiterhugo.debuglens.domain.model.DiagnosticInput
import io.github.demiterhugo.debuglens.domain.model.ExceptionSummary
import io.github.demiterhugo.debuglens.domain.model.StackFrame

/** Parses the first exception chain. Suppressed branches are not its root cause. */
internal class StackTraceParser {
    fun parse(input: DiagnosticInput): ParsedTrace {
        val causes = mutableListOf<ExceptionSummary>()
        var firstFrame: StackFrame? = null
        var firstSourceFrame: StackFrame? = null
        var firstApplicationFrame: StackFrame? = null
        var suppressedIndent: Int? = null
        var anrEvidence: String? = null

        for (rawLine in input.text.lineSequence()) {
            val line = removeLogPrefix(rawLine)
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            if (anrEvidence == null && anrMarker.matches(trimmed)) {
                anrEvidence = trimmed
            }
            if (trimmed.startsWith("Suppressed: ")) {
                suppressedIndent = minOf(suppressedIndent ?: indent, indent)
                continue
            }

            val header = exceptionHeader.matchEntire(trimmed)
            if (header != null && isThrowableType(header.groups["type"]!!.value)) {
                val suppression = suppressedIndent
                if (suppression != null) {
                    if (indent >= suppression) continue
                    suppressedIndent = null
                }
                val isCause = header.groups["cause"] != null
                if (!isCause && causes.isNotEmpty()) break

                causes += ExceptionSummary(
                    type = header.groups["type"]!!.value,
                    message = header.groups["message"]?.value?.takeIf { it.isNotBlank() },
                )
                firstFrame = null
                firstSourceFrame = null
                firstApplicationFrame = null
                continue
            }

            if (causes.isEmpty() || suppressedIndent != null) continue
            val frame = parseFrame(trimmed) ?: continue
            if (firstFrame == null) firstFrame = frame
            if (frame.lineNumber != null) {
                if (firstSourceFrame == null) firstSourceFrame = frame
                if (firstApplicationFrame == null && frameworkPrefixes.none(frame.symbol::startsWith)) {
                    firstApplicationFrame = frame
                }
            }
        }

        return ParsedTrace(
            causes = causes.toList(),
            location = firstApplicationFrame ?: firstSourceFrame ?: firstFrame,
            anrEvidence = anrEvidence,
        )
    }

    private fun removeLogPrefix(line: String): String {
        for (prefix in logPrefixes) {
            val match = prefix.find(line) ?: continue
            return line.substring(match.range.last + 1)
        }
        return line
    }

    private fun parseFrame(line: String): StackFrame? {
        val match = stackFrame.matchEntire(line) ?: return null
        val source = sourceLocation.matchEntire(match.groupValues[2])
        return StackFrame(
            symbol = match.groupValues[1].substringAfterLast('/'),
            fileName = source?.groupValues?.get(1),
            lineNumber = source?.groupValues?.get(2)?.toIntOrNull(),
        )
    }

    private fun isThrowableType(type: String): Boolean {
        if (!type.endsWith("Exception") && !type.endsWith("Error")) return false
        return type.split('.').all { segment ->
            segment.isNotEmpty() &&
                (segment.first().isLetter() || segment.first() == '_' || segment.first() == '$') &&
                segment.all { it.isLetterOrDigit() || it == '_' || it == '$' }
        }
    }

    private companion object {
        val exceptionHeader = Regex(
            "(?:(?<cause>Caused by:)\\s+|Exception in thread \"[^\"]+\"\\s+)?" +
                "(?<type>[A-Za-z_\$][\\w.\$]*)(?::[ \\t]*(?<message>.*))?",
        )
        val stackFrame = Regex("at\\s+([^\\s(]+)\\(([^)]+)\\)\\s*")
        val sourceLocation = Regex("(.+):(\\d+)")
        val anrMarker = Regex("(?:ANR in\\s+.+|(?:Reason:\\s*)?Input dispatching timed out\\b.*)")
        val logPrefixes = listOf(
            Regex("^\\d{4}-\\d{2}-\\d{2}\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?\\s+\\d+-\\d+\\s+\\S+\\s+\\S+\\s+[VDIWEF] {1,2}"),
            Regex("^(?:\\d{2}-\\d{2}|\\d{4}-\\d{2}-\\d{2})\\s+\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?\\s+\\d+\\s+\\d+\\s+[VDIWEF]\\s+[^:]+: ?"),
            Regex("^[VDIWEF]/[^(:]+(?:\\(\\s*\\d+\\))?: ?"),
            Regex("^(?:[VDIWEF]\\s+)?AndroidRuntime: ?"),
        )
        val frameworkPrefixes = listOf("java.", "javax.", "android.", "androidx.", "kotlin.", "kotlinx.", "dalvik.", "sun.")
    }
}

internal data class ParsedTrace(
    val causes: List<ExceptionSummary>,
    val location: StackFrame?,
    val anrEvidence: String?,
)
