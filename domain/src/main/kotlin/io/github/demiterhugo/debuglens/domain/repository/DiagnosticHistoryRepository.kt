package io.github.demiterhugo.debuglens.domain.repository

import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import kotlinx.coroutines.flow.Flow

interface DiagnosticHistoryRepository {
    /** Emits immutable snapshots, newest first. Retention is implementation-specific. */
    fun observeSessions(): Flow<List<DiagnosticSession>>

    /** Implementations must be safe to call from the main thread. */
    suspend fun save(analysis: DiagnosticAnalysis): DiagnosticSession

    /** Atomically returns the newest session with the same normalized input or saves the analysis. */
    suspend fun saveOrReuse(analysis: DiagnosticAnalysis): DiagnosticSession

    /** Removes the session by ID. A missing session is already deleted. */
    suspend fun delete(sessionId: String)

    suspend fun clear()
}
