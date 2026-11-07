package io.github.demiterhugo.debuglens.data.repository

import android.content.ContentResolver
import android.os.OperationCanceledException
import io.github.demiterhugo.debuglens.data.local.CancellableDocumentReader
import io.github.demiterhugo.debuglens.domain.repository.LogcatImportRepository
import io.github.demiterhugo.debuglens.domain.repository.LogcatImportResult
import io.github.demiterhugo.debuglens.domain.usecase.ExtractLogcatIncidentUseCase
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class AndroidLogcatImportRepository(
    resolver: ContentResolver,
) : LogcatImportRepository {
    private val documents = CancellableDocumentReader(resolver)
    private val extractIncident = ExtractLogcatIncidentUseCase()

    override suspend fun importDocument(documentId: String): LogcatImportResult =
        try {
            documents.read(documentId) { stream, _ -> readIncident(stream) }
                ?: LogcatImportResult.Failure.UNREADABLE
        } catch (_: CharacterCodingException) {
            LogcatImportResult.Failure.INVALID_ENCODING
        } catch (_: IOException) {
            LogcatImportResult.Failure.UNREADABLE
        } catch (_: SecurityException) {
            LogcatImportResult.Failure.UNREADABLE
        } catch (_: OperationCanceledException) {
            currentCoroutineContext().ensureActive()
            LogcatImportResult.Failure.UNREADABLE
        }

    private fun readIncident(stream: InputStream): LogcatImportResult {
        val buffer = ByteArray(MAX_FILE_BYTES + 1)
        var size = 0
        while (size < buffer.size) {
            val read = stream.read(buffer, size, buffer.size - size)
            if (read == -1) break
            if (read == 0) throw IOException("The document stream did not make progress")
            size += read
        }
        if (size > MAX_FILE_BYTES) return LogcatImportResult.Failure.FILE_TOO_LARGE

        val bytes = ByteBuffer.wrap(buffer, 0, size)
        val bom = if (size >= 2) bytes.getShort(0).toInt() and 0xFFFF else 0
        val charset = if (bom == 0xFEFF || bom == 0xFFFE) Charsets.UTF_16 else Charsets.UTF_8
        val text = charset.newDecoder().decode(bytes).toString().removePrefix("\uFEFF")
        if ('\u0000' in text) return LogcatImportResult.Failure.INVALID_ENCODING
        return extractIncident(text)
    }

    private companion object {
        const val MAX_FILE_BYTES = 2 * 1024 * 1024
    }
}
