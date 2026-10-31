package io.github.demiterhugo.debuglens.domain.repository

import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import kotlinx.coroutines.flow.Flow

interface DiagnosticHistoryRepository {
    /** Emits immutable snapshots, newest first. Retention is implementation-specific. */
    fun observeSessions(): Flow<List<DiagnosticSession>>

    /** Implementations must be safe to call from the main thread. */
    suspend fun save(analysis: DiagnosticAnalysis): DiagnosticSession

    suspend fun clear()
}
