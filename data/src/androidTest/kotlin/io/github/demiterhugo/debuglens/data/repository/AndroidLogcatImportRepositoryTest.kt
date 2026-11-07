package io.github.demiterhugo.debuglens.data.repository

import android.content.ContentProvider
import android.content.ContentValues
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import androidx.test.platform.app.InstrumentationRegistry
import io.github.demiterhugo.debuglens.domain.repository.LogcatIncidentResult
import java.io.FileNotFoundException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AndroidLogcatImportRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = AndroidLogcatImportRepository(context.contentResolver)
    private val document = "content://${context.packageName}.logcat-test/trace"

    @After
    fun resetProvider() {
        LogcatTestProvider.openDocument = null
    }

    @Test
    fun rejectsAMalformedDocumentEvenWhenItContainsAValidIncident() = runBlocking {
        val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe()
        readEnd.use {
            val trace = "11-07 10:11:12.100  2200  2200 E AndroidRuntime: FATAL EXCEPTION: main\n" +
                "11-07 10:11:12.100  2200  2200 E AndroidRuntime: java.lang.IllegalStateException: captured\n"
            ParcelFileDescriptor.AutoCloseOutputStream(writeEnd).use {
                it.write(byteArrayOf(0xC0.toByte(), 0x80.toByte(), 10) + trace.encodeToByteArray())
            }
            LogcatTestProvider.openDocument = { AssetFileDescriptor(readEnd, 0, AssetFileDescriptor.UNKNOWN_LENGTH) }

            assertEquals(LogcatIncidentResult.Failure.INVALID_ENCODING, repository.importDocument(document))
        }
    }

    @Test
    fun cancelsTheProviderWhileOpeningTheDocument() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val receivedCancellation = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        LogcatTestProvider.openDocument = { signal ->
            signal?.setOnCancelListener {
                receivedCancellation.complete(Unit)
                release.countDown()
            }
            entered.complete(Unit)
            release.await(10, TimeUnit.SECONDS)
            signal?.throwIfCanceled()
            throw FileNotFoundException()
        }

        try {
            val importJob = launch { repository.importDocument(document) }
            withTimeout(5_000) {
                entered.await()
                importJob.cancel()
                receivedCancellation.await()
                importJob.join()
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun closesTheDescriptorWhileWaitingForMorePipeData() = runBlocking {
        val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe()
        LogcatTestProvider.openDocument = { AssetFileDescriptor(readEnd, 0, AssetFileDescriptor.UNKNOWN_LENGTH) }
        try {
            val importJob = launch { repository.importDocument(document) }
            val poll = StructPollfd().apply {
                fd = readEnd.fileDescriptor
                events = OsConstants.POLLIN.toShort()
            }
            assertEquals(1, Os.write(writeEnd.fileDescriptor, byteArrayOf(65), 0, 1))
            withTimeout(5_000) {
                // The writer stays open. Once the byte is consumed, the reader still needs EOF.
                while (Os.poll(arrayOf(poll), 0) != 0) delay(10)
                importJob.cancelAndJoin()
            }
            assertFalse(readEnd.fileDescriptor.valid())
        } finally {
            writeEnd.close()
            readEnd.close()
        }
    }
}

class LogcatTestProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openTypedAssetFile(
        uri: Uri,
        mimeTypeFilter: String,
        opts: Bundle?,
        signal: CancellationSignal?,
    ): AssetFileDescriptor = openDocument?.invoke(signal) ?: throw FileNotFoundException()

    override fun getType(uri: Uri): String = "text/plain"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        @Volatile
        var openDocument: ((CancellationSignal?) -> AssetFileDescriptor)? = null
    }
}
