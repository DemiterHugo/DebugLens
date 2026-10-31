package io.github.demiterhugo.debuglens.data.repository

import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticHistoryRepository
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class InMemoryDiagnosticHistoryRepository(
    private val capacity: Int = 20,
) : DiagnosticHistoryRepository {
    init {
        require(capacity > 0) { "History capacity must be positive" }
    }

    private val _sessions = MutableStateFlow<List<DiagnosticSession>>(emptyList())
    private val sessions = _sessions.asStateFlow()

    override fun observeSessions(): Flow<List<DiagnosticSession>> = sessions

    override suspend fun save(analysis: DiagnosticAnalysis): DiagnosticSession {
        val session = DiagnosticSession(UUID.randomUUID().toString(), analysis)
        _sessions.update { previous -> (listOf(session) + previous).take(capacity) }
        return session
    }

    override suspend fun clear() {
        _sessions.value = emptyList()
    }
}
