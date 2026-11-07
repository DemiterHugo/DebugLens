package io.github.demiterhugo.debuglens.data.repository

import androidx.test.platform.app.InstrumentationRegistry
import io.github.demiterhugo.debuglens.domain.repository.LogcatIncidentResult
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class AndroidLogcatCaptureRepositoryTest {
    private val crash = """
        11-07 10:11:12.100  2200  2200 E AndroidRuntime: FATAL EXCEPTION: main
        11-07 10:11:12.100  2200  2200 E AndroidRuntime: java.lang.IllegalStateException: café 😀
    """.trimIndent()

    @Test
    fun readsAnIncidentFromTheChildProcess() = runBlocking {
        val repository = AndroidLogcatCaptureRepository { dumpProcess(exitCode = 0) }

        assertEquals(LogcatIncidentResult.Found(crash), repository.captureLatestIncident())
    }

    @Test
    fun keepsTheLatestValidIncidentAfterAMalformedOlderRecord() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dump = File.createTempFile("logcat-capture-", ".txt", context.cacheDir)
        try {
            val older = "11-07 09:00:00.100  1100  1100 E AndroidRuntime: old entry: "
            dump.writeBytes(older.encodeToByteArray() + byteArrayOf(0xC0.toByte(), 0x80.toByte()) +
                "\n$crash\n".encodeToByteArray())
            val repository = AndroidLogcatCaptureRepository {
                ProcessBuilder("/system/bin/cat", dump.absolutePath).redirectErrorStream(true).start()
            }

            assertEquals(LogcatIncidentResult.Found(crash), repository.captureLatestIncident())
        } finally {
            dump.delete()
        }
    }

    @Test
    fun rejectsOutputFromAFailedCommand() = runBlocking {
        val repository = AndroidLogcatCaptureRepository { dumpProcess(exitCode = 1) }

        assertEquals(LogcatIncidentResult.Failure.UNREADABLE, repository.captureLatestIncident())
    }

    @Test
    fun cancelingTheReadTerminatesTheChildProcess() = runBlocking {
        val started = CompletableDeferred<Process>()
        val repository = AndroidLogcatCaptureRepository {
            ProcessBuilder("/system/bin/sleep", "60").redirectErrorStream(true).start().also {
                started.complete(it)
            }
        }
        val capture = launch { repository.captureLatestIncident() }

        withTimeout(5_000) {
            val process = started.await()
            capture.cancelAndJoin()
            assertNotEquals(0, runInterruptible(Dispatchers.IO) { process.waitFor() })
        }
    }

    private fun dumpProcess(exitCode: Int): Process = ProcessBuilder(
        "/system/bin/sh", "-c", "printf '%s\\n' \"\$1\"; exit \"\$2\"", "logcat-test", crash, exitCode.toString(),
    ).redirectErrorStream(true).start()
}
