package io.github.demiterhugo.debuglens.data.local

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
internal abstract class DiagnosticHistoryDao {
    @Transaction
    @Query("SELECT * FROM diagnostic_sessions ORDER BY sequence DESC")
    abstract fun observeSessions(): Flow<List<StoredDiagnosticSession>>

    @Transaction
    @Query("SELECT * FROM diagnostic_sessions WHERE input = :input ORDER BY sequence DESC LIMIT 1")
    protected abstract suspend fun findLatestByInput(input: String): StoredDiagnosticSession?

    @Insert
    protected abstract suspend fun insertSession(session: DiagnosticSessionEntity)

    @Insert
    protected abstract suspend fun insertCauses(causes: List<DiagnosticCauseEntity>)

    @Query("""
        DELETE FROM diagnostic_sessions WHERE sequence NOT IN
        (SELECT sequence FROM diagnostic_sessions ORDER BY sequence DESC LIMIT :capacity)
    """)
    protected abstract suspend fun prune(capacity: Int)

    @Transaction
    open suspend fun save(
        session: DiagnosticSessionEntity,
        causes: List<DiagnosticCauseEntity>,
        capacity: Int,
        reuseMatchingInput: Boolean = false,
    ): StoredDiagnosticSession {
        if (reuseMatchingInput) {
            findLatestByInput(session.input)?.let { return it }
        }
        insertSession(session)
        insertCauses(causes)
        prune(capacity)
        return StoredDiagnosticSession(session, causes)
    }

    @Query("DELETE FROM diagnostic_sessions WHERE id = :sessionId")
    abstract suspend fun delete(sessionId: String)

    @Query("DELETE FROM diagnostic_sessions")
    abstract suspend fun clear()
}
