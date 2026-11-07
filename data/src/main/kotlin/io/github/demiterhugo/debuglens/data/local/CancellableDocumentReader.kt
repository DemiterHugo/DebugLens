package io.github.demiterhugo.debuglens.data.local

import android.content.ContentResolver
import android.content.res.AssetFileDescriptor
import android.net.Uri
import android.os.CancellationSignal
import java.io.InputStream
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

internal class CancellableDocumentReader(private val resolver: ContentResolver) {
    suspend fun <T> read(documentId: String, consume: (InputStream, CoroutineContext) -> T): T? =
        withContext(Dispatchers.IO) {
            val uri = Uri.parse(documentId)
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) return@withContext null

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
                    val asset = resolver.openAssetFileDescriptor(uri, "r", signal) ?: return@runCatching null
                    openAsset.set(asset)
                    try {
                        continuation.context.ensureActive()
                        consume(asset.createInputStream(), continuation.context)
                    } finally {
                        // The asset owns the stream's descriptor. Exactly one path closes it.
                        openAsset.getAndSet(null)?.close()
                    }
                })
            }
        }

    private companion object {
        // Cancellation can close a descriptor even when readers occupy the IO dispatcher.
        val cancellationDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
}
