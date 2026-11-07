package io.github.demiterhugo.debuglens.domain.repository

fun interface LogcatImportRepository {
    /** Reads the selected document off the main thread and extracts one incident. */
    suspend fun importDocument(documentId: String): LogcatIncidentResult
}
