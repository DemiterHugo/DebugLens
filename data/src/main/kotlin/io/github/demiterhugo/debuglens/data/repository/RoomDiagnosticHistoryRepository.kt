package io.github.demiterhugo.debuglens.data.repository

import io.github.demiterhugo.debuglens.data.local.DiagnosticHistoryDao
import io.github.demiterhugo.debuglens.data.local.toDomain
import io.github.demiterhugo.debuglens.data.local.toStored
import io.github.demiterhugo.debuglens.domain.model.DiagnosticAnalysis
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticHistoryRepository
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn

internal class RoomDiagnosticHistoryRepository(private val dao: DiagnosticHistoryDao) : DiagnosticHistoryRepository {
    override fun observeSessions(): Flow<List<DiagnosticSession>> = dao.observeSessions()
        .map { sessions -> sessions.map { it.toDomain() } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)

    override suspend fun save(analysis: DiagnosticAnalysis): DiagnosticSession {
        val session = DiagnosticSession(UUID.randomUUID().toString(), analysis)
        val stored = session.toStored()
        dao.save(stored.session, stored.causes, CAPACITY)
        return session
    }

    override suspend fun clear() = dao.clear()

    companion object {
        const val CAPACITY = 20
    }
}
