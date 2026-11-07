package io.github.demiterhugo.debuglens.domain.repository

fun interface DiagnosticReportWriter {
    /** Creates a new report in the selected folder and throws if writing fails. */
    suspend fun writeNew(folderId: String, report: String)
}
