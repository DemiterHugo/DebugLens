package io.github.demiterhugo.debuglens.data.repository

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.system.Os
import android.system.OsConstants
import android.system.StructPollfd
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDiagnosticReportWriterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val authority = "${context.packageName}.report-test"
    private val folderId = DocumentsContract.buildTreeDocumentUri(authority, "folder").toString()
    private val writer = AndroidDiagnosticReportWriter(context.contentResolver)

    @After
    fun resetProvider() {
        ReportTestProvider.openReport = null
        ReportTestProvider.deleteReport = null
    }

    @Test
    fun writesUtf8OnlyToTheNewDocumentAndRejectsAnExistingDocumentUri() = runBlocking {
        val existing = File.createTempFile("existing-", ".txt", context.cacheDir)
        val created = File.createTempFile("created-", ".txt", context.cacheDir)
        try {
            existing.writeText("Original contents")
            ReportTestProvider.openReport = { id, _, _ ->
                val file = if (id == "existing") existing else created
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)
            }
            val report = "DebugLens\ncafé 😀\n"
            writer.writeNew(folderId, report)
            val existingId = DocumentsContract.buildDocumentUri(authority, "existing").toString()
            val failure = runCatching { writer.writeNew(existingId, "overwrite") }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
            assertEquals(report, created.readText())
            assertEquals("Original contents", existing.readText())
        } finally {
            existing.delete()
            created.delete()
        }
    }

    @Test
    fun cancelingDuringOpenSignalsTheProviderAndDiscardsTheNewDocument() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val signaled = CompletableDeferred<Unit>()
        val deleted = CompletableDeferred<String>()
        val release = CountDownLatch(1)
        ReportTestProvider.openReport = { _, _, signal ->
            signal?.setOnCancelListener {
                signaled.complete(Unit)
                release.countDown()
            }
            entered.complete(Unit)
            release.await(10, TimeUnit.SECONDS)
            signal?.throwIfCanceled()
            throw FileNotFoundException()
        }
        ReportTestProvider.deleteReport = { deleted.complete(it) }

        try {
            val write = launch { writer.writeNew(folderId, "report") }
            withTimeout(5_000) {
                entered.await()
                write.cancelAndJoin()
                signaled.await()
                assertEquals("created", deleted.await())
            }
        } finally {
            release.countDown()
        }
    }

    @Test
    fun cancelingAFullPipeUnblocksTheWriterAndDiscardsTheNewDocument() = runBlocking {
        val (readEnd, writeEnd) = ParcelFileDescriptor.createPipe()
        val deleted = CompletableDeferred<String>()
        ReportTestProvider.openReport = { _, _, _ -> writeEnd }
        ReportTestProvider.deleteReport = { deleted.complete(it) }
        try {
            val write = launch { writer.writeNew(folderId, "x".repeat(512 * 1024)) }
            val poll = StructPollfd().apply {
                fd = readEnd.fileDescriptor
                events = OsConstants.POLLIN.toShort()
            }
            withTimeout(5_000) {
                // The pipe contains data, but the reader never drains it.
                while (Os.poll(arrayOf(poll), 0) == 0) delay(10)
                write.cancelAndJoin()
                assertEquals("created", deleted.await())
            }
        } finally {
            readEnd.close()
            writeEnd.close()
        }
    }
}

class ReportTestProvider : DocumentsProvider() {
    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor =
        MatrixCursor(projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor =
        throw UnsupportedOperationException()

    override fun queryChildDocuments(
        parentDocumentId: String, projection: Array<out String>?, sortOrder: String?,
    ): Cursor = throw UnsupportedOperationException()

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        parentDocumentId == "folder" && documentId == "created"

    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
        check(parentDocumentId == "folder" && mimeType == "text/plain" && displayName == "debuglens-diagnostico.txt")
        return "created"
    }

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
        openReport?.invoke(documentId, mode, signal) ?: throw FileNotFoundException()

    override fun deleteDocument(documentId: String) {
        deleteReport?.invoke(documentId)
    }

    companion object {
        @Volatile var openReport: ((String, String, CancellationSignal?) -> ParcelFileDescriptor)? = null
        @Volatile var deleteReport: ((String) -> Unit)? = null
    }
}
