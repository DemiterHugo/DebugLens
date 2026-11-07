package io.github.demiterhugo.debuglens.data.repository

import android.content.Context
import androidx.room3.Room
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.demiterhugo.debuglens.data.local.DiagnosticHistoryDatabase
import io.github.demiterhugo.debuglens.data.local.toStored
import io.github.demiterhugo.debuglens.domain.model.DiagnosticResult
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.usecase.AnalyzeDiagnosticUseCase
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomDiagnosticHistoryRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: DiagnosticHistoryDatabase
    private lateinit var repository: RoomDiagnosticHistoryRepository
    private val diskNames = mutableListOf<String>()

    @Before
    fun createDatabase() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, DiagnosticHistoryDatabase::class.java)
            .setDriver(AndroidSQLiteDriver()).build()
        repository = RoomDiagnosticHistoryRepository(database.diagnosticHistoryDao())
    }

    @After
    fun closeDatabase() {
        if (::database.isInitialized) database.close()
        diskNames.forEach { context.deleteDatabase(it) }
    }

    @Test
    fun storedSessionSurvivesClosingAndReopeningTheDatabase() = runBlocking {
        val name = diskName()
        val expected = DiagnosticHistoryStorage.open(context, name).use { storage ->
            storage.repository.save(analysis())
        }
        DiagnosticHistoryStorage.open(context, name).use { storage ->
            val restored = withTimeout(10_000) { storage.repository.observeSessions().first() }
            assertEquals(listOf(expected), restored)
        }
    }

    @Test
    fun clearingIsDurableAfterTheDatabaseIsReopened() = runBlocking {
        val name = diskName()
        DiagnosticHistoryStorage.open(context, name).use { storage ->
            storage.repository.save(analysis())
            storage.repository.clear()
        }
        DiagnosticHistoryStorage.open(context, name).use { storage ->
            assertTrue(withTimeout(10_000) { storage.repository.observeSessions().first() }.isEmpty())
        }
    }

    @Test
    fun retainsTheNewestTwentySessionsAndCascadesEvictedCauses() = runBlocking {
        val saved = (0..20).map { index -> repository.save(analysis("message-$index")) }
        assertEquals(saved.reversed().take(20), snapshot())
        assertEquals(40L, causeCount())
    }

    @Test
    fun clearingRemovesBothSessionsAndTheirCauseRows() = runBlocking {
        repository.save(analysis())
        repository.save(analysis())
        val previous = snapshot()
        repository.clear()
        assertTrue(snapshot().isEmpty())
        assertEquals(0L, causeCount())
        assertEquals(2, previous.size)
    }

    @Test
    fun failedCauseInsertionRollsBackTheWholeSessionAndLeavesTheDatabaseUsable() = runBlocking {
        val stored = DiagnosticSession("invalid-batch", analysis()).toStored()
        val cause = stored.causes.first()
        val failure = runCatching { database.diagnosticHistoryDao().save(stored.session, listOf(cause, cause), 20) }
        assertTrue(failure.isFailure)
        assertTrue(snapshot().isEmpty())
        assertEquals(0L, causeCount())
        val valid = repository.save(analysis())
        assertEquals(listOf(valid), snapshot())
    }

    @Test
    fun flowPublishesSavedAndClearedSnapshots() = runBlocking {
        val emissions = Channel<List<DiagnosticSession>>(Channel.UNLIMITED)
        val job = launch { repository.observeSessions().collect { emissions.send(it) } }
        try {
            assertTrue(withTimeout(10_000) { emissions.receive() }.isEmpty())
            val saved = repository.save(analysis())
            val previous = withTimeout(10_000) { emissions.receive() }
            assertEquals(listOf(saved), previous)
            repository.clear()
            assertTrue(withTimeout(10_000) { emissions.receive() }.isEmpty())
            assertEquals(listOf(saved), previous)
        } finally {
            job.cancelAndJoin()
            emissions.close()
        }
    }

    @Test
    fun concurrentSavesDoNotLoseSessionsOrProducePartialCauseChains() = runBlocking {
        val saved = (0..7).map { index -> async(Dispatchers.Default) { repository.save(analysis("message-$index")) } }.awaitAll()
        val restored = snapshot()
        assertEquals(saved.toSet(), restored.toSet())
        assertEquals(16L, causeCount())
    }

    @Test
    fun identicalAnalysesGetDistinctIdentitiesInInsertionOrder() = runBlocking {
        val first = repository.save(analysis())
        val second = repository.save(analysis())
        assertNotEquals(first.id, second.id)
        assertEquals(listOf(second, first), snapshot())
    }

    @Test
    fun captureReusesTheNewestMatchingSessionAfterReopeningWithoutReorderingHistory() = runBlocking {
        val name = diskName()
        val existing = DiagnosticHistoryStorage.open(context, name).use { storage ->
            val first = storage.repository.save(analysis())
            val second = storage.repository.save(analysis())
            val newest = storage.repository.save(analysis("unrelated"))
            listOf(newest, second, first)
        }
        DiagnosticHistoryStorage.open(context, name).use { storage ->
            assertEquals(existing[1], storage.repository.saveOrReuse(analysis()))
            assertEquals(existing, withTimeout(10_000) { storage.repository.observeSessions().first() })
        }
    }

    @Test
    fun concurrentCapturesOfTheSameIncidentStoreOneSessionAndOneCauseChain() = runBlocking {
        val captured = analysis()
        val sessions = List(8) { async(Dispatchers.Default) { repository.saveOrReuse(captured) } }.awaitAll()

        assertEquals(1, sessions.toSet().size)
        assertEquals(listOf(sessions.first()), snapshot())
        assertEquals(2L, causeCount())
    }

    private suspend fun snapshot() = withTimeout(10_000) { repository.observeSessions().first() }

    private suspend fun causeCount(): Long = database.useReaderConnection { connection ->
        connection.usePrepared("SELECT COUNT(*) FROM diagnostic_causes") { statement ->
            check(statement.step())
            statement.getLong(0)
        }
    }

    private fun diskName(): String = "history-test-${UUID.randomUUID()}.db".also { diskNames += it }

    private fun analysis(message: String = "missing \"value\" 😃") = (AnalyzeDiagnosticUseCase()(
        "java.lang.RuntimeException: wrapper\n" +
            "Caused by: java.lang.NullPointerException: $message\n\tat sample.Profile.render(Profile.kt:42)",
    ) as DiagnosticResult.Success).analysis
}
