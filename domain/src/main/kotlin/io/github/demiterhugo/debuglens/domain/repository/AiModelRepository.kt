package io.github.demiterhugo.debuglens.domain.repository

interface AiModelRepository {
    suspend fun modelStatus(): AiModelStatus
    suspend fun installModel(documentId: String): AiModelInstallResult
    suspend fun removeModel(): Boolean
}

enum class AiModelStatus { UNSUPPORTED, MISSING, READY }

enum class AiModelInstallResult { INSTALLED, INVALID_FILE, TOO_LARGE, UNREADABLE }
