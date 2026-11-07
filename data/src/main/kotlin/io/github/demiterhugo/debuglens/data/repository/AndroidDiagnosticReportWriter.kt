package io.github.demiterhugo.debuglens.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticReportWriter
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidDiagnosticReportWriter(
    private val resolver: ContentResolver,
) : DiagnosticReportWriter {
    override suspend fun writeNew(folderId: String, report: String): Unit =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            val openStream = AtomicReference<ParcelFileDescriptor.AutoCloseOutputStream?>()
            continuation.invokeOnCancellation {
                // Cancel the remote open and close the local stream independently.
                Dispatchers.IO.dispatch(EmptyCoroutineContext) { runCatching { signal.cancel() } }
                closeDispatcher.dispatch(EmptyCoroutineContext) {
                    runCatching { openStream.getAndSet(null)?.close() }
                }
            }
            // Creation has no CancellationSignal. A slow provider must never hold up the caller.
            Dispatchers.IO.dispatch(EmptyCoroutineContext) {
                if (!continuation.isActive) return@dispatch
                var created: Uri? = null
                val result = runCatching {
                    val document = createReport(folderId)
                    created = document
                    continuation.context.ensureActive()
                    val descriptor = resolver.openFileDescriptor(document, "w", signal)
                        ?: throw IOException("Document provider unavailable")
                    val output = ParcelFileDescriptor.AutoCloseOutputStream(descriptor)
                    openStream.set(output)
                    try {
                        continuation.context.ensureActive()
                        output.write(report.encodeToByteArray())
                    } finally {
                        openStream.getAndSet(null)?.close()
                    }
                    continuation.context.ensureActive()
                    document
                }
                result.fold(
                    onSuccess = { document ->
                        continuation.resume(Unit) { _, _, _ ->
                            Dispatchers.IO.dispatch(EmptyCoroutineContext) { discard(document) }
                        }
                    },
                    onFailure = { failure ->
                        continuation.resumeWith(Result.failure(failure))
                        created?.let(::discard)
                    },
                )
            }
        }

    private fun createReport(folderId: String): Uri {
        val tree = Uri.parse(folderId)
        require(tree.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(tree))
        val folder = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return DocumentsContract.createDocument(resolver, folder, "text/plain", "debuglens-diagnostico.txt")
            ?: throw IOException("Could not create the report")
    }

    private fun discard(document: Uri) {
        // Only ever delete a document returned by this operation's createDocument call.
        runCatching { DocumentsContract.deleteDocument(resolver, document) }
    }

    private companion object {
        val closeDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
}
