package io.github.demiterhugo.debuglens.data.repository

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.CancellationSignal
import android.os.OperationCanceledException
import io.github.demiterhugo.debuglens.domain.repository.LogcatImportRepository
import io.github.demiterhugo.debuglens.domain.repository.LogcatImportResult
import io.github.demiterhugo.debuglens.domain.usecase.ExtractLogcatIncidentUseCase
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class AndroidLogcatImportRepository(
    private val resolver: ContentResolver,
) : LogcatImportRepository {
    private val extractIncident = ExtractLogcatIncidentUseCase()

    override suspend fun importDocument(documentId: String): LogcatImportResult =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(documentId)
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
                return@withContext LogcatImportResult.Failure.UNREADABLE
            }
            try {
                readDocument(uri)
            } catch (_: CharacterCodingException) {
                LogcatImportResult.Failure.INVALID_ENCODING
            } catch (_: IOException) {
                LogcatImportResult.Failure.UNREADABLE
            } catch (_: SecurityException) {
                LogcatImportResult.Failure.UNREADABLE
            } catch (_: OperationCanceledException) {
                ensureActive()
                LogcatImportResult.Failure.UNREADABLE
            }
        }

    private suspend fun readDocument(uri: Uri): LogcatImportResult =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            val openAsset = AtomicReference<AssetFileDescriptor?>()
            continuation.invokeOnCancellation {
                cancellationDispatcher.dispatch(EmptyCoroutineContext) {
                    runCatching { openAsset.getAndSet(null)?.close() }
                    runCatching { signal.cancel() }
                }
            }
            if (!continuation.isActive) return@suspendCancellableCoroutine

            continuation.resumeWith(runCatching {
                val asset = resolver.openAssetFileDescriptor(uri, "r", signal)
                    ?: return@runCatching LogcatImportResult.Failure.UNREADABLE
                openAsset.set(asset)
                try {
                    continuation.context.ensureActive()
                    readIncident(asset.createInputStream())
                } finally {
                    // The asset owns the stream's descriptor. Exactly one path closes it.
                    openAsset.getAndSet(null)?.close()
                }
            })
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
        // Cancellation must be able to close a descriptor even when readers occupy the IO dispatcher.
        val cancellationDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
}
