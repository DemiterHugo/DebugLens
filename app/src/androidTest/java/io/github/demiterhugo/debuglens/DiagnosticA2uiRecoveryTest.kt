package io.github.demiterhugo.debuglens

import androidx.a2ui.compose.ui.A2uiMessageProcessor
import androidx.a2ui.model.processor.A2uiMessageProcessor
import androidx.a2ui.model.protocol.A2uiComponentPayload
import androidx.a2ui.model.protocol.A2uiServerToClientMessage
import androidx.a2ui.model.protocol.A2uiUpdateComponentsMessage
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.demiterhugo.debuglens.domain.model.DiagnosticResult
import io.github.demiterhugo.debuglens.domain.model.DiagnosticSession
import io.github.demiterhugo.debuglens.domain.usecase.AnalyzeDiagnosticUseCase
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticCatalog
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticSurfaceController
import io.github.demiterhugo.debuglens.ui.diagnostic.diagnosticSurfaceCopy
import io.github.demiterhugo.debuglens.ui.home.DiagnosticResultCard
import io.github.demiterhugo.debuglens.ui.theme.DebugLensTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticA2uiRecoveryTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rejectsAnUnknownComponentAndRecoversThroughTheRealSdk() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val sdk = A2uiMessageProcessor(listOf(DiagnosticCatalog))
        val processor = object : A2uiMessageProcessor by sdk {
            private var corruptFirstUpdate = true
            override fun processMessage(message: A2uiServerToClientMessage) {
                if (message is A2uiUpdateComponentsMessage && corruptFirstUpdate) {
                    corruptFirstUpdate = false
                    sdk.processMessage(
                        A2uiUpdateComponentsMessage(
                            message.surfaceId, listOf(A2uiComponentPayload("root", "WebView", emptyMap())),
                        ),
                    )
                } else {
                    sdk.processMessage(message)
                }
            }
        }
        val controller = DiagnosticSurfaceController(scope, Dispatchers.Default, { processor })
        val analysis = (AnalyzeDiagnosticUseCase()("java.lang.NullPointerException: example") as DiagnosticResult.Success).analysis
        val session = DiagnosticSession("recovery-test", analysis)
        try {
            composeRule.setContent {
                DebugLensTheme {
                    val state by controller.state.collectAsStateWithLifecycle()
                    val copy = diagnosticSurfaceCopy(analysis)
                    LaunchedEffect(copy) { controller.show(session, copy) }
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        DiagnosticResultCard(session.id, state, controller::retry)
                    }
                }
            }
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag("a2ui_surface_error").fetchSemanticsNodes().isNotEmpty()
            }

            val context = InstrumentationRegistry.getInstrumentation().targetContext
            composeRule.onNodeWithText(context.getString(R.string.a2ui_retry)).performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag("a2ui_surface_ready").fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText(context.getString(R.string.category_null_reference)).assertIsDisplayed()
        } finally {
            scope.cancel()
        }
    }
}
