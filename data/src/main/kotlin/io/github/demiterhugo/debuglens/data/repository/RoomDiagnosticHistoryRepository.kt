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

    override suspend fun save(analysis: DiagnosticAnalysis): DiagnosticSession =
        store(analysis, reuseMatchingInput = false)

    override suspend fun saveOrReuse(analysis: DiagnosticAnalysis): DiagnosticSession =
        store(analysis, reuseMatchingInput = true)

    private suspend fun store(analysis: DiagnosticAnalysis, reuseMatchingInput: Boolean): DiagnosticSession {
        val candidate = DiagnosticSession(UUID.randomUUID().toString(), analysis).toStored()
        return dao.save(candidate.session, candidate.causes, CAPACITY, reuseMatchingInput).toDomain()
    }

    override suspend fun delete(sessionId: String) = dao.delete(sessionId)

    override suspend fun clear() = dao.clear()

    companion object {
        const val CAPACITY = 20
    }
}
