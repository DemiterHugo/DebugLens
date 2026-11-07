package io.github.demiterhugo.debuglens.domain.usecase

import io.github.demiterhugo.debuglens.domain.model.DiagnosticInput
import io.github.demiterhugo.debuglens.domain.repository.LogcatImportResult

/** Selects the last Java crash or ANR in an adb logcat threadtime dump. */
class ExtractLogcatIncidentUseCase {
    operator fun invoke(text: String): LogcatImportResult {
        var incident: Incident? = null
        for (line in text.lineSequence()) {
            val match = record.matchEntire(line) ?: continue
            val (pid, tid, tag, message) = match.destructured
            val fatalHeader = message.startsWith("FATAL EXCEPTION:") ||
                message.startsWith("*** FATAL EXCEPTION IN SYSTEM PROCESS:")
            when {
                tag == "AndroidRuntime" && fatalHeader -> {
                    incident = Incident(pid to tid).also { it.append(line) }
                }
                tag == "ActivityManager" && message.startsWith("ANR in ") -> {
                    incident = Incident(runtimeThread = null).also { it.append(line) }
                }
                tag == "AndroidRuntime" && incident?.runtimeThread == (pid to tid) -> {
                    incident.append(line)
                }
            }
        }
        return incident?.result() ?: LogcatImportResult.Failure.NO_INCIDENT
    }

    private class Incident(val runtimeThread: Pair<String, String>?) {
        private val trace = StringBuilder()
        private var tooLong = false

        fun append(line: String) {
            if (tooLong) return
            val separatorLength = if (trace.isEmpty()) 0 else 1
            if (line.length > DiagnosticInput.MAX_LENGTH - trace.length - separatorLength) {
                tooLong = true
                return
            }
            if (separatorLength != 0) trace.append('\n')
            trace.append(line)
        }

        fun result(): LogcatImportResult = if (tooLong) {
            LogcatImportResult.Failure.INCIDENT_TOO_LONG
        } else {
            LogcatImportResult.Imported(trace.toString())
        }
    }

    private companion object {
        val record = Regex(
            """^(?:\d{4}-)?\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+\s+(\d+)\s+(\d+)\s+[EF]\s+(AndroidRuntime|ActivityManager)\s*: ?(.*)$""",
        )
    }
}
