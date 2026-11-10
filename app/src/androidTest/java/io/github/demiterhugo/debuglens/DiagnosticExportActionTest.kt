package io.github.demiterhugo.debuglens

import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticExportAction
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticExportStatus
import io.github.demiterhugo.debuglens.ui.diagnostic.DiagnosticReportDestination
import io.github.demiterhugo.debuglens.ui.theme.DebugLensTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DiagnosticExportActionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun restoresReportOptionsOnlyForTheSameDiagnostic() {
        val restoration = StateRestorationTester(composeRule)
        var sessionAfterRestore = "first"
        restoration.setContent {
            val sessionId = remember { sessionAfterRestore }
            DebugLensTheme {
                DiagnosticExportAction(sessionId, hasAdvice = true, DiagnosticExportStatus.IDLE) { _, _, _ -> }
            }
        }
        composeRule.onNodeWithTag("export_diagnostic").performClick()
        composeRule.onNodeWithTag("export_include_trace").performClick().assertIsOn()
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("export_include_trace").assertIsOn()

        composeRule.runOnIdle { sessionAfterRestore = "second" }
        restoration.emulateSavedInstanceStateRestore()
        composeRule.onNodeWithTag("share_diagnostic_text").assertDoesNotExist()
        composeRule.onNodeWithTag("export_diagnostic").performClick()
        composeRule.onNodeWithTag("export_include_trace").assertIsOff()
    }

    @Test
    fun canShareWhileAFileIsBeingWritten() {
        var destination: DiagnosticReportDestination? = null
        composeRule.setContent {
            DebugLensTheme {
                DiagnosticExportAction("session", hasAdvice = false, DiagnosticExportStatus.WRITING) { target, _, _ ->
                    destination = target
                }
            }
        }
        composeRule.onNodeWithTag("export_diagnostic").performClick()
        composeRule.onNodeWithTag("export_choose_folder").assertIsNotEnabled()
        composeRule.onNodeWithTag("share_diagnostic_text").performClick()
        composeRule.runOnIdle { assertEquals(DiagnosticReportDestination.SHARE_TEXT, destination) }
    }
}
