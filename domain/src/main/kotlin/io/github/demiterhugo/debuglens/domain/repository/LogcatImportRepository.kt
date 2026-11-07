package io.github.demiterhugo.debuglens.domain.repository

fun interface LogcatImportRepository {
    /** Reads the selected document off the main thread and extracts one incident. */
    suspend fun importDocument(documentId: String): LogcatImportResult
}

sealed interface LogcatImportResult {
    data class Imported(val trace: String) : LogcatImportResult

    enum class Failure : LogcatImportResult {
        UNREADABLE,
        FILE_TOO_LARGE,
        INVALID_ENCODING,
        NO_INCIDENT,
        INCIDENT_TOO_LONG,
    }
}
