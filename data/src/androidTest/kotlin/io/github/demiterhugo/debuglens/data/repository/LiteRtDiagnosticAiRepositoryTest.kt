package io.github.demiterhugo.debuglens.data.repository

import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetFileDescriptor
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.github.demiterhugo.debuglens.domain.repository.AiModelInstallResult
import io.github.demiterhugo.debuglens.domain.repository.AiModelStatus
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

class LiteRtDiagnosticAiRepositoryTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext
    private val directory = File.createTempFile("model-test-", ".tmp", target.cacheDir).also {
        check(it.delete() && it.mkdir())
    }
    private val context = object : ContextWrapper(target) {
        override fun getApplicationContext(): Context = this
        override fun getNoBackupFilesDir(): File = directory
    }
    private val repository = LiteRtDiagnosticAiRepository(context)
    private val active = File(directory, "diagnostic-model.litertlm")
    private val pending = File(directory, "diagnostic-model.pending.litertlm")
    private val installedBytes = "previous model".encodeToByteArray()

    @After
    fun cleanUp() {
        LogcatTestProvider.openDocument = null
        directory.deleteRecursively()
    }

    @Test
    fun rejectsAnIncompatibleModelAndKeepsTheInstalledCopy() = runBlocking {
        assumeTrue(repository.modelStatus() != AiModelStatus.UNSUPPORTED)
        active.writeBytes(installedBytes)
        val candidate = File(directory, "candidate.litertlm")
        // Pass the cheap magic and size checks with an unsupported format version.
        val invalidModel = ByteArray(32 * 1024)
        "LITERTLM".encodeToByteArray().copyInto(invalidModel)
        invalidModel[8] = 127
        candidate.writeBytes(invalidModel)
        LogcatTestProvider.openDocument = {
            AssetFileDescriptor(
                ParcelFileDescriptor.open(candidate, ParcelFileDescriptor.MODE_READ_ONLY),
                0,
                candidate.length(),
            )
        }

        val result = repository.installModel("content://${context.packageName}.logcat-test/model")

        assertEquals(AiModelInstallResult.INVALID_FILE, result)
        assertArrayEquals(installedBytes, active.readBytes())
        assertFalse(pending.exists())
    }

    @Test
    fun statusCleansAbandonedCopiesAndPreservesOtherFiles() = runBlocking {
        active.writeBytes(installedBytes)
        pending.writeText("interrupted copy")
        val legacy = File(directory, "diagnostic-model-123.part").apply { writeText("old copy") }
        val unrelated = File(directory, "unrelated.part").apply { writeText("other data") }

        repository.modelStatus()

        assertFalse(pending.exists())
        assertFalse(legacy.exists())
        assertArrayEquals(installedBytes, active.readBytes())
        assertEquals("other data", unrelated.readText())
    }
}
