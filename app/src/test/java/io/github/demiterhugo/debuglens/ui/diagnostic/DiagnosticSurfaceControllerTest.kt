package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.a2ui.model.processor.A2uiMessageProcessor
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.protocol.A2uiClientToServerMessage
import androidx.a2ui.model.protocol.A2uiCreateSurfaceMessage
import androidx.a2ui.model.protocol.A2uiDeleteSurfaceMessage
import androidx.a2ui.model.protocol.A2uiServerToClientMessage
import androidx.a2ui.model.protocol.A2uiUpdateComponentsMessage
import androidx.a2ui.model.protocol.A2uiUpdateDataModelMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticSurfaceControllerTest {
    @Test
    fun createsTheSurfaceThenInitializesDataAndComponents() = runTest {
        val processor = FakeProcessor()
        val controller = DiagnosticSurfaceController(backgroundScope, StandardTestDispatcher(testScheduler), { processor })

        controller.show(diagnosticSession(), diagnosticCopy())

        assertTrue(processor.messages[0] is A2uiCreateSurfaceMessage)
        assertTrue(processor.messages[1] is A2uiUpdateDataModelMessage)
        assertTrue(processor.messages[2] is A2uiUpdateComponentsMessage)
        assertEquals(DIAGNOSTIC_CATALOG_ID, processor.created().catalogId)
        assertEquals(1, processor.messages.map { it.surfaceId }.toSet().size)
    }

    @Test
    fun recompositionAndLocalizedUpdatesDoNotResetChecklistData() = runTest {
        val processor = FakeProcessor()
        val controller = DiagnosticSurfaceController(backgroundScope, StandardTestDispatcher(testScheduler), { processor })
        val session = diagnosticSession()
        val copy = diagnosticCopy()
        controller.show(session, copy)
        controller.show(session, copy)
        assertEquals(3, processor.messages.size)

        controller.show(session, copy.copy(text = copy.text.copy(title = "Referencia nula")))

        assertEquals(1, processor.messages.filterIsInstance<A2uiUpdateDataModelMessage>().size)
        assertEquals(1, processor.messages.filterIsInstance<A2uiCreateSurfaceMessage>().size)
        assertEquals("Referencia nula", processor.components().single { it.id == "title" }.properties["text"])
    }

    @Test
    fun identicalTextInANewSessionGetsAFreshSurface() = runTest {
        val processor = FakeProcessor()
        val controller = DiagnosticSurfaceController(backgroundScope, StandardTestDispatcher(testScheduler), { processor })
        val session = diagnosticSession()
        controller.show(session, diagnosticCopy())
        val firstId = processor.created().surfaceId

        controller.show(session.copy(id = "session-2"), diagnosticCopy())

        assertNotEquals(firstId, processor.created().surfaceId)
        assertEquals(firstId, processor.messages.filterIsInstance<A2uiDeleteSurfaceMessage>().single().surfaceId)
        assertEquals("session-2", controller.state.value.sessionId)
        assertEquals(2, processor.messages.filterIsInstance<A2uiUpdateDataModelMessage>().size)
    }

    @Test
    fun registeredActionsChangeTheComponentTreeWithoutResettingBindings() = runTest {
        val processor = FakeProcessor()
        val controller = DiagnosticSurfaceController(backgroundScope, StandardTestDispatcher(testScheduler), { processor })
        controller.show(diagnosticSession(), diagnosticCopy())
        val id = processor.created().surfaceId

        processor.events.emit(action(id, TOGGLE_CAUSES, "toggle_causes"))
        runCurrent()
        assertTrue(processor.components().any { it.id == "cause_0_type" })

        processor.events.emit(action(id, TOGGLE_CAUSES, "toggle_causes"))
        processor.events.emit(action(id, TOGGLE_SOURCE, "toggle_source"))
        runCurrent()

        assertFalse(processor.components().any { it.id == "cause_0_type" })
        assertTrue(processor.components().any { it.id == "source" })
        assertEquals(1, processor.messages.filterIsInstance<A2uiUpdateDataModelMessage>().size)
    }

    @Test
    fun ignoresStaleSurfacesUnknownActionsAndMismatchedComponentIds() = runTest {
        val processor = FakeProcessor()
        val controller = DiagnosticSurfaceController(backgroundScope, StandardTestDispatcher(testScheduler), { processor })
        controller.show(diagnosticSession(), diagnosticCopy())
        val id = processor.created().surfaceId

        processor.events.emit(action("old-surface", TOGGLE_SOURCE, "toggle_source"))
        processor.events.emit(action(id, "open_url", "toggle_source"))
        processor.events.emit(action(id, TOGGLE_SOURCE, "unknown-component"))
        runCurrent()

        assertEquals(3, processor.messages.size)
    }

    @Test
    fun errorsCanBeRetriedWithoutRepeatingTheDomainAnalysis() = runTest {
        val processor = FakeProcessor()
        val controller = DiagnosticSurfaceController(backgroundScope, StandardTestDispatcher(testScheduler), { processor })
        controller.show(diagnosticSession(), diagnosticCopy())
        val id = processor.created().surfaceId
        processor.events.emit(A2uiClientErrorMessage("UNSUPPORTED_COMPONENT", id, "Unknown component"))
        runCurrent()
        assertTrue(controller.state.value.hasError)

        controller.retry()

        assertFalse(controller.state.value.hasError)
        assertEquals("session-1", controller.state.value.sessionId)
        assertNotEquals(id, processor.created().surfaceId)
    }

    @Test
    fun lateModelsAndErrorsDoNotResurrectAClearedSurface() = runTest {
        val processor = FakeProcessor()
        val controller = DiagnosticSurfaceController(backgroundScope, StandardTestDispatcher(testScheduler), { processor })
        controller.show(diagnosticSession(), diagnosticCopy())
        val id = processor.created().surfaceId
        controller.clear()
        processor.activeSurfaces.value = listOf(FakeSurface(id))
        processor.events.emit(A2uiClientErrorMessage("LATE_ERROR", id, "Old error"))
        runCurrent()

        assertEquals(DiagnosticSurfaceState(), controller.state.value)
    }

    @Test
    fun exposesOnlyTheCurrentlyRequestedSurfaceModel() = runTest {
        val processor = FakeProcessor()
        val controller = DiagnosticSurfaceController(backgroundScope, StandardTestDispatcher(testScheduler), { processor })
        controller.show(diagnosticSession(), diagnosticCopy())
        val oldId = processor.created().surfaceId
        controller.show(diagnosticSession(id = "session-2"), diagnosticCopy())
        val newSurface = FakeSurface(processor.created().surfaceId)
        processor.activeSurfaces.value = listOf(FakeSurface(oldId), newSurface)
        runCurrent()

        assertEquals(newSurface, controller.state.value.surface)
    }

    @Test
    fun cancellationStopsProcessingWithoutProducingAnError() = runTest {
        val processor = FakeProcessor()
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher + Job())
        val controller = DiagnosticSurfaceController(scope, dispatcher, { processor })
        runCurrent()

        scope.cancel()
        runCurrent()

        assertTrue(processor.stopped)
        assertFalse(controller.state.value.hasError)
    }

    @Test
    fun retryReplacesAFailedProcessorAndRestartsItsCollectors() = runTest {
        val failed = FakeProcessor(failOnCollect = true)
        val healthy = FakeProcessor()
        var creations = 0
        val controller = DiagnosticSurfaceController(backgroundScope, StandardTestDispatcher(testScheduler), {
            if (creations++ == 0) failed else healthy
        })
        controller.show(diagnosticSession(), diagnosticCopy())
        runCurrent()
        assertTrue(controller.state.value.hasError)

        controller.retry()
        runCurrent()

        assertEquals(2, creations)
        assertFalse(controller.state.value.hasError)
        assertEquals(3, healthy.messages.size)
        healthy.events.emit(action(healthy.created().surfaceId, TOGGLE_SOURCE, "toggle_source"))
        runCurrent()
        assertTrue(healthy.components().any { it.id == "source" })
        assertNull(controller.state.value.surface)
    }

    private fun action(surface: String, name: String, component: String) =
        A2uiClientEventMessage(name, surface, component, timestamp = 0)

    private class FakeSurface(override val id: String) : A2uiSurfaceModel

    private class FakeProcessor(private val failOnCollect: Boolean = false) : A2uiMessageProcessor {
        override val activeSurfaces = MutableStateFlow<List<A2uiSurfaceModel>>(emptyList())
        val events = MutableSharedFlow<A2uiClientToServerMessage>(extraBufferCapacity = 16)
        override val outboundEvents = events
        val messages = mutableListOf<A2uiServerToClientMessage>()
        var stopped = false

        override fun processMessage(message: A2uiServerToClientMessage) { messages += message }
        override fun processError(error: A2uiClientErrorMessage) { events.tryEmit(error) }
        override suspend fun collectMessages() {
            try {
                if (failOnCollect) error("Processor failed")
                awaitCancellation()
            } finally {
                stopped = true
            }
        }

        fun created() = messages.filterIsInstance<A2uiCreateSurfaceMessage>().last()
        fun components() = messages.filterIsInstance<A2uiUpdateComponentsMessage>().last().components
    }
}
