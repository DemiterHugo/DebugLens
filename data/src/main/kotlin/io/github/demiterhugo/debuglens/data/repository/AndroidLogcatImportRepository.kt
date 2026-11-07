package io.github.demiterhugo.debuglens.data.repository

import android.content.ContentResolver
import android.os.OperationCanceledException
import io.github.demiterhugo.debuglens.data.local.CancellableDocumentReader
import io.github.demiterhugo.debuglens.data.local.LogcatDumpReader
import io.github.demiterhugo.debuglens.domain.repository.LogcatImportRepository
import io.github.demiterhugo.debuglens.domain.repository.LogcatIncidentResult
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class AndroidLogcatImportRepository(
    resolver: ContentResolver,
) : LogcatImportRepository {
    private val documents = CancellableDocumentReader(resolver)

    override suspend fun importDocument(documentId: String): LogcatIncidentResult =
        try {
            documents.read(documentId) { stream, _ -> LogcatDumpReader.readDocument(stream) }
                ?: LogcatIncidentResult.Failure.UNREADABLE
        } catch (_: IOException) {
            LogcatIncidentResult.Failure.UNREADABLE
        } catch (_: SecurityException) {
            LogcatIncidentResult.Failure.UNREADABLE
        } catch (_: OperationCanceledException) {
            currentCoroutineContext().ensureActive()
            LogcatIncidentResult.Failure.UNREADABLE
        }
}
