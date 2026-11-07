package io.github.demiterhugo.debuglens.data.repository

import io.github.demiterhugo.debuglens.data.local.LogcatDumpReader
import io.github.demiterhugo.debuglens.domain.repository.LogcatCaptureRepository
import io.github.demiterhugo.debuglens.domain.repository.LogcatIncidentResult
import java.io.IOException
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class AndroidLogcatCaptureRepository internal constructor(
    private val startLogcat: () -> Process,
) : LogcatCaptureRepository {
    constructor() : this({
        // logd limits the unprivileged reader to its UID, including previous app processes.
        ProcessBuilder(
            "/system/bin/logcat", "-b", "crash", "-v", "threadtime", "-t", "500", "AndroidRuntime:E", "*:S",
        ).redirectErrorStream(true).start()
    })

    override suspend fun captureLatestIncident(): LogcatIncidentResult =
        try {
            withTimeoutOrNull(CAPTURE_TIMEOUT_MS) { readSnapshot() } ?: LogcatIncidentResult.Failure.TIMED_OUT
        } catch (_: IOException) {
            LogcatIncidentResult.Failure.UNREADABLE
        } catch (_: SecurityException) {
            LogcatIncidentResult.Failure.UNREADABLE
        }

    private suspend fun readSnapshot(): LogcatIncidentResult = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            if (!continuation.isActive) return@suspendCancellableCoroutine
            continuation.resumeWith(runCatching {
                val process = startLogcat()
                continuation.invokeOnCancellation {
                    // Terminate the child to release a blocked pipe read without blocking the caller.
                    cancellationDispatcher.dispatch(EmptyCoroutineContext) { process.destroy() }
                }
                try {
                    continuation.context.ensureActive()
                    process.outputStream.close()
                    val result = process.inputStream.use(LogcatDumpReader::readCapture)
                    if (result == LogcatIncidentResult.Failure.DUMP_TOO_LARGE || process.waitFor() == 0) {
                        result
                    } else {
                        LogcatIncidentResult.Failure.UNREADABLE
                    }
                } finally {
                    process.destroy()
                }
            })
        }
    }

    private companion object {
        const val CAPTURE_TIMEOUT_MS = 8_000L
        val cancellationDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
}
