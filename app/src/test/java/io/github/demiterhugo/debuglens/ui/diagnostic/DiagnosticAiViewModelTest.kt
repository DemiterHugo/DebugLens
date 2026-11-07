package io.github.demiterhugo.debuglens.ui.diagnostic

import io.github.demiterhugo.debuglens.domain.repository.AiModelInstallResult
import io.github.demiterhugo.debuglens.domain.repository.AiModelRepository
import io.github.demiterhugo.debuglens.domain.repository.AiModelStatus
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticAdviceGenerator
import io.github.demiterhugo.debuglens.domain.repository.DiagnosticAiResult
import io.github.demiterhugo.debuglens.domain.usecase.GenerateDiagnosticAdviceUseCase
import io.github.demiterhugo.debuglens.ui.home.MainDispatcherRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticAiViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun keepsTheModelAndExistingAdviceWhenReplacementFails() = runTest {
        val repository = FakeAiRepository().apply { status = AiModelStatus.MISSING }
        val model = DiagnosticAiViewModel(repository, GenerateDiagnosticAdviceUseCase(repository))
        val session = diagnosticSession(
            text = "java.lang.NullPointerException: missing name\n" +
                "\tat example.Profile.render(Profile.kt:42)\n" +
                "Unrelated background noise",
        )
        model.selectSession(session.id)
        advanceUntilIdle()
        model.generate(session)
        assertEquals(0, repository.requests.size)

        model.installModel("valid-model")
        advanceUntilIdle()
        model.generate(session)
        advanceUntilIdle()
        assertEquals("Verifica el valor nulo.", model.state.value.advice)
        assertTrue(repository.requests.single().contains("Profile.kt:42"))
        assertFalse(repository.requests.single().contains("Unrelated background noise"))

        model.installModel("invalid-model")
        advanceUntilIdle()
        assertEquals(AiModelInstallResult.INVALID_FILE, model.state.value.modelError)
        assertEquals(AiModelStatus.READY, model.state.value.model)
        assertEquals("Verifica el valor nulo.", model.state.value.advice)
    }

    @Test
    fun discardsALateAnswerAfterChangingTheSelectedSession() = runTest {
        val repository = FakeAiRepository().apply {
            generate = {
                try {
                    awaitCancellation()
                } catch (_: CancellationException) {
                    DiagnosticAiResult.Advice("Obsolete answer")
                }
            }
        }
        val model = DiagnosticAiViewModel(repository, GenerateDiagnosticAdviceUseCase(repository))
        val first = diagnosticSession("first")
        model.selectSession(first.id)
        advanceUntilIdle()
        model.generate(first)
        runCurrent()
        assertTrue(model.state.value.generating)

        model.selectSession("second")
        advanceUntilIdle()
        assertEquals("second", model.state.value.sessionId)
        assertNull(model.state.value.advice)
        assertNull(model.state.value.generationError)
        assertFalse(model.state.value.generating)
    }

    private class FakeAiRepository : AiModelRepository, DiagnosticAdviceGenerator {
        var status = AiModelStatus.READY
        val requests = mutableListOf<String>()
        var generate: suspend () -> DiagnosticAiResult = { DiagnosticAiResult.Advice("Verifica el valor nulo.") }

        override suspend fun modelStatus() = status

        override suspend fun installModel(documentId: String): AiModelInstallResult {
            if (documentId == "invalid-model") return AiModelInstallResult.INVALID_FILE
            status = AiModelStatus.READY
            return AiModelInstallResult.INSTALLED
        }

        override suspend fun removeModel(): Boolean {
            status = AiModelStatus.MISSING
            return true
        }

        override suspend fun generateAdvice(prompt: String): DiagnosticAiResult {
            requests += prompt
            return generate()
        }
    }
}
