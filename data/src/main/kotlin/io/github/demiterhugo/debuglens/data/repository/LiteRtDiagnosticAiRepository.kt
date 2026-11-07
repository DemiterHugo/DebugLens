package io.github.demiterhugo.debuglens.data.repository

import android.content.Context
import android.os.Build
import android.os.Process
import android.system.Os
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig
import io.github.demiterhugo.debuglens.data.local.CancellableDocumentReader
import io.github.demiterhugo.debuglens.domain.repository.AiModelInstallResult
import io.github.demiterhugo.debuglens.domain.repository.AiModelRepository
import io.github.demiterhugo.debuglens.domain.repository.AiModelStatus
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticAdviceGenerator
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticAiResult
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LiteRtDiagnosticAiRepository(context: Context) : AiModelRepository, DiagnosticAdviceGenerator {
    private val appContext = context.applicationContext
    private val documents = CancellableDocumentReader(appContext.contentResolver)
    private val modelDirectory get() = appContext.noBackupFilesDir
    private val modelFile get() = File(modelDirectory, "diagnostic-model.litertlm")
    private val modelLock = Mutex()
    private val supported = Process.is64Bit() && Build.SUPPORTED_ABIS.any { it == "arm64-v8a" || it == "x86_64" }

    override suspend fun modelStatus(): AiModelStatus = withModelLock {
        removeAbandonedCopies()
        when {
            !supported -> AiModelStatus.UNSUPPORTED
            modelFile.isFile -> AiModelStatus.READY
            else -> AiModelStatus.MISSING
        }
    }

    override suspend fun installModel(documentId: String): AiModelInstallResult = withModelLock {
        if (!supported) return@withModelLock AiModelInstallResult.UNREADABLE
        removeAbandonedCopies()
        val pending = File(modelDirectory, PENDING_MODEL_NAME)
        if (pending.exists()) return@withModelLock AiModelInstallResult.UNREADABLE
        try {
            val copied = copyCandidate(documentId, pending)
            if (copied != AiModelInstallResult.INSTALLED) return@withModelLock copied
            if (!canOpenModel(pending)) return@withModelLock AiModelInstallResult.INVALID_FILE

            ensureActive()
            Os.rename(pending.absolutePath, modelFile.absolutePath)
            AiModelInstallResult.INSTALLED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LinkageError) {
            ensureActive()
            AiModelInstallResult.UNREADABLE
        } catch (_: Exception) {
            ensureActive()
            AiModelInstallResult.UNREADABLE
        } finally {
            pending.delete()
        }
    }

    private suspend fun copyCandidate(documentId: String, pending: File): AiModelInstallResult =
        documents.read(documentId) { stream, context ->
            val header = ByteArray(MODEL_MAGIC.size)
            var offset = 0
            while (offset < header.size) {
                val count = stream.read(header, offset, header.size - offset)
                if (count == -1) return@read AiModelInstallResult.INVALID_FILE
                if (count == 0) return@read AiModelInstallResult.UNREADABLE
                offset += count
            }
            if (!header.contentEquals(MODEL_MAGIC)) return@read AiModelInstallResult.INVALID_FILE

            pending.outputStream().buffered().use { output ->
                output.write(header)
                val buffer = ByteArray(64 * 1024)
                var size = header.size.toLong()
                while (true) {
                    context.ensureActive()
                    val count = stream.read(buffer)
                    if (count == -1) break
                    if (count == 0) throw IOException("The model stream did not make progress")
                    size += count
                    if (size > MAX_MODEL_BYTES) return@read AiModelInstallResult.TOO_LARGE
                    output.write(buffer, 0, count)
                }
                if (size <= MODEL_HEADER_BYTES) return@read AiModelInstallResult.INVALID_FILE
            }
            AiModelInstallResult.INSTALLED
        } ?: AiModelInstallResult.UNREADABLE

    private suspend fun canOpenModel(model: File): Boolean = try {
        withConversation(model, cacheDir = ":nocache") { Unit }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        false
    }

    override suspend fun removeModel(): Boolean = withModelLock {
        removeAbandonedCopies()
        val model = modelFile
        !model.exists() || model.delete()
    }

    override suspend fun generateAdvice(prompt: String): DiagnosticAiResult = withModelLock {
        val model = modelFile
        if (!supported || !model.isFile) return@withModelLock DiagnosticAiResult.Failure.UNAVAILABLE
        try {
            withConversation(model) { conversation ->
                val advice = StringBuilder()
                try {
                    conversation.sendMessageAsync(prompt).collect { chunk ->
                        chunk.contents.contents.filterIsInstance<Content.Text>().forEach { part ->
                            advice.append(part.text.take((MAX_ADVICE_CHARS - advice.length).coerceAtLeast(0)))
                        }
                    }
                } catch (cancelled: CancellationException) {
                    runCatching { conversation.cancelProcess() }
                    throw cancelled
                }
                val text = advice.toString().trim().trimEnd { it.isHighSurrogate() }
                if (text.isEmpty()) DiagnosticAiResult.Failure.GENERATION_FAILED
                else DiagnosticAiResult.Advice(text)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: LinkageError) {
            ensureActive()
            DiagnosticAiResult.Failure.UNAVAILABLE
        } catch (_: Exception) {
            ensureActive()
            DiagnosticAiResult.Failure.GENERATION_FAILED
        }
    }

    private suspend fun <T> withModelLock(operation: suspend CoroutineScope.() -> T): T =
        modelLock.withLock { withContext(Dispatchers.IO, block = operation) }

    private fun removeAbandonedCopies() {
        modelDirectory.listFiles()?.forEach { file ->
            if (file.name == PENDING_MODEL_NAME ||
                (file.name.startsWith("diagnostic-model-") && file.name.endsWith(".part"))
            ) {
                if (file.isFile) file.delete()
            }
        }
    }

    private suspend fun <T> withConversation(
        model: File,
        cacheDir: String? = null,
        useConversation: suspend (Conversation) -> T,
    ): T {
        Engine.setNativeMinLogSeverity(LogSeverity.INFINITY)
        val engine = Engine(
            EngineConfig(modelPath = model.absolutePath, maxNumTokens = 2_048, cacheDir = cacheDir),
        )
        try {
            engine.initialize()
            currentCoroutineContext().ensureActive()
            val config = ConversationConfig(
                samplerConfig = SamplerConfig(topK = 16, topP = 0.9, temperature = 0.2, seed = 1),
                automaticToolCalling = false,
                maxOutputToken = 128,
            )
            return engine.createConversation(config).use { useConversation(it) }
        } finally {
            if (engine.isInitialized()) engine.close()
        }
    }

    private companion object {
        val MODEL_MAGIC = "LITERTLM".encodeToByteArray()
        const val PENDING_MODEL_NAME = "diagnostic-model.pending.litertlm"
        const val MODEL_HEADER_BYTES = 16 * 1024
        const val MAX_MODEL_BYTES = 1024L * 1024 * 1024
        const val MAX_ADVICE_CHARS = 2_048
    }
}
