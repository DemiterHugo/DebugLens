package io.github.demiterhugo.debuglens.ui.diagnostic

import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.processor.A2uiMessageProcessor
import androidx.a2ui.model.processor.A2uiSurfaceModel
import androidx.a2ui.model.protocol.A2uiClientErrorMessage
import androidx.a2ui.model.protocol.A2uiClientEventMessage
import androidx.a2ui.model.protocol.A2uiCreateSurfaceMessage
import androidx.a2ui.model.protocol.A2uiDeleteSurfaceMessage
import androidx.a2ui.model.protocol.A2uiUpdateComponentsMessage
import androidx.a2ui.model.protocol.A2uiUpdateDataModelMessage
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class DiagnosticSurfaceState(
    val sessionId: String? = null,
    val surface: A2uiSurfaceModel? = null,
    val hasError: Boolean = false,
)

/** Owns one surface; all host methods and outbound events are coordinated on the UI scope. */
internal class DiagnosticSurfaceController(
    private val scope: CoroutineScope,
    private val dispatcher: CoroutineDispatcher,
    private val processorFactory: () -> A2uiMessageProcessor = { A2uiMessageProcessor(listOf(DiagnosticCatalog)) },
    private val factory: DiagnosticSurfaceFactory = DiagnosticSurfaceFactory(),
) {
    private val _state = MutableStateFlow(DiagnosticSurfaceState())
    val state = _state.asStateFlow()
    private var currentSession: DiagnosticSession? = null
    private var currentCopy: DiagnosticSurfaceCopy? = null
    private var surfaceId: String? = null
    private var generation = 0L
    private var causesExpanded = false
    private var sourceExpanded = false
    private var processor = processorFactory()
    private var processorScope = createProcessorScope()
    private var processorFailed = false
    private var latestRequestId = Long.MIN_VALUE

    init {
        startProcessing()
    }

    private fun createProcessorScope() = CoroutineScope(scope.coroutineContext + Job(scope.coroutineContext[Job]))

    private fun startProcessing() {
        val observedProcessor = processor
        processorScope.launch(start = CoroutineStart.UNDISPATCHED) {
            observedProcessor.outboundEvents.collect { event ->
                if (observedProcessor !== processor) return@collect
                when (event) {
                    is A2uiClientErrorMessage -> if (event.surfaceId == surfaceId) {
                        _state.update { it.copy(hasError = true) }
                    }
                    is A2uiClientEventMessage -> handleAction(event)
                }
            }
        }
        processorScope.launch {
            observedProcessor.activeSurfaces.collect { surfaces ->
                if (observedProcessor !== processor) return@collect
                val active = surfaces.firstOrNull { it.id == surfaceId }
                _state.update { it.copy(surface = active) }
            }
        }
        processorScope.launch(dispatcher) {
            try {
                observedProcessor.collectMessages()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                scope.launch {
                    if (observedProcessor === processor) {
                        processorFailed = true
                        _state.update { it.copy(hasError = true) }
                    }
                }
            }
        }
    }

    fun show(session: DiagnosticSession, copy: DiagnosticSurfaceCopy, requestId: Long = 0L) {
        if (requestId < latestRequestId) return
        latestRequestId = requestId
        if (currentSession?.id == session.id && currentSession?.analysis == session.analysis) {
            if (currentCopy != copy) {
                currentCopy = copy
                updateComponents()
            }
            return
        }
        startSurface(session, copy)
    }

    fun clear(requestId: Long = 0L) {
        if (requestId < latestRequestId) return
        latestRequestId = requestId
        surfaceId?.let { processor.processMessage(A2uiDeleteSurfaceMessage(it)) }
        surfaceId = null
        currentSession = null
        currentCopy = null
        _state.value = DiagnosticSurfaceState()
    }

    fun retry() {
        val session = currentSession ?: return
        val copy = currentCopy ?: return
        startSurface(session, copy)
    }

    private fun startSurface(session: DiagnosticSession, copy: DiagnosticSurfaceCopy) {
        if (processorFailed) {
            processorScope.cancel()
            processor = processorFactory()
            processorScope = createProcessorScope()
            processorFailed = false
            surfaceId = null
            startProcessing()
        }
        surfaceId?.let { processor.processMessage(A2uiDeleteSurfaceMessage(it)) }
        val nextId = "diagnostic-${++generation}"
        surfaceId = nextId
        currentSession = session
        currentCopy = copy
        causesExpanded = false
        sourceExpanded = false
        _state.value = DiagnosticSurfaceState(sessionId = session.id)
        val document = factory.create(session.analysis, copy)
        processor.processMessage(A2uiCreateSurfaceMessage(nextId, DIAGNOSTIC_CATALOG_ID))
        processor.processMessage(A2uiUpdateDataModelMessage(nextId, value = document.initialData))
        processor.processMessage(A2uiUpdateComponentsMessage(nextId, document.components))
    }

    private fun handleAction(event: A2uiClientEventMessage) {
        if (event.surfaceId != surfaceId || _state.value.hasError) return
        when {
            event.type == TOGGLE_CAUSES && event.componentId == "toggle_causes" &&
                (currentSession?.analysis?.causes?.size ?: 0) > 1 -> causesExpanded = !causesExpanded
            event.type == TOGGLE_SOURCE && event.componentId == "toggle_source" -> sourceExpanded = !sourceExpanded
            else -> return
        }
        updateComponents()
    }

    private fun updateComponents() {
        val id = surfaceId ?: return
        val session = currentSession ?: return
        val copy = currentCopy ?: return
        val document = factory.create(session.analysis, copy, causesExpanded, sourceExpanded)
        processor.processMessage(A2uiUpdateComponentsMessage(id, document.components))
    }
}
