package io.github.demiterhugo.debuglens.data.repository

import io.github.demiterhugo.debuglens.domain.model.DiagnosticResult
import io.github.demiterhugo.debuglens.domain.usecase.AnalyzeDiagnosticUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InMemoryDiagnosticHistoryRepositoryTest {
    @Test
    fun startsEmpty() = runTest {
        assertTrue(InMemoryDiagnosticHistoryRepository().observeSessions().first().isEmpty())
    }

    @Test
    fun emitsNewestSessionsFirst() = runTest {
        val repository = InMemoryDiagnosticHistoryRepository()
        val first = repository.save(analysis("java.lang.IllegalStateException: first"))
        val second = repository.save(analysis("java.lang.NullPointerException: second"))

        assertEquals(listOf(second, first), repository.observeSessions().first())
    }

    @Test
    fun discardsOnlyTheOldestEntriesWhenCapacityIsExceeded() = runTest {
        val repository = InMemoryDiagnosticHistoryRepository(capacity = 2)
        repository.save(analysis("java.lang.IllegalStateException: oldest"))
        val retained = repository.save(analysis("java.lang.IllegalStateException: retained"))
        val newest = repository.save(analysis("java.lang.IllegalStateException: newest"))

        assertEquals(listOf(newest, retained), repository.observeSessions().first())
    }

    @Test
    fun repeatedInputReceivesDistinctSessionIds() = runTest {
        val repository = InMemoryDiagnosticHistoryRepository()
        val input = analysis("java.lang.NullPointerException")

        assertNotEquals(repository.save(input).id, repository.save(input).id)
        assertEquals(2, repository.observeSessions().first().size)
    }

    @Test
    fun clearingHistoryDoesNotMutatePreviousSnapshots() = runTest {
        val repository = InMemoryDiagnosticHistoryRepository()
        val session = repository.save(analysis("java.lang.NullPointerException"))
        val snapshot = repository.observeSessions().first()

        repository.clear()

        assertTrue(repository.observeSessions().first().isEmpty())
        assertEquals(listOf(session), snapshot)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonPositiveCapacity() {
        InMemoryDiagnosticHistoryRepository(capacity = 0)
    }

    private fun analysis(text: String) =
        (AnalyzeDiagnosticUseCase()(text) as DiagnosticResult.Success).analysis
}
