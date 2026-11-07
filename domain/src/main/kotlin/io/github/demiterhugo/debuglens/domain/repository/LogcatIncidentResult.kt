package io.github.demiterhugo.debuglens.domain.repository

sealed interface LogcatIncidentResult {
    data class Found(val trace: String) : LogcatIncidentResult

    enum class Failure : LogcatIncidentResult {
        UNREADABLE,
        DUMP_TOO_LARGE,
        INVALID_ENCODING,
        NO_INCIDENT,
        INCIDENT_TOO_LONG,
        TIMED_OUT,
    }
}
