package io.github.demiterhugo.debuglens

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicReference
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.AssumptionViolatedException
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearPreviousHistory() {
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("history_loading").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("retry_history").assertDoesNotExist()
        if (composeRule.onAllNodesWithTag("clear_history").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("clear_history").performScrollTo().performClick()
        }
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("history_empty").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun launchesWithHiltAndShowsHome() {
        assertEquals("io.github.demiterhugo.debuglens", composeRule.activity.packageName)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_input_title))
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aboutRemainsOpenAfterActivityRecreationAndCanBeDismissed() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_info_button))
            .performScrollTo().performClick()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_about_description))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_close))
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_about_description))
            .assertDoesNotExist()
    }

    @Test
    fun emptyInputShowsValidationFeedback() {
        composeRule.onNodeWithTag("analyze_input").performScrollTo().performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.input_error_empty))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("analysis_result").assertDoesNotExist()
    }

    @Test
    fun analyzesPastedTextAndRetainsTheResultAfterRecreation() {
        val input = "java.lang.RuntimeException: wrapper\n" +
            "Caused by: java.lang.NullPointerException: missing name\n" +
            "\tat example.Profile.render(Profile.kt:42)"
        analyze(input)
        composeRule.onNodeWithText("Profile.kt:42").performScrollTo().assertIsDisplayed()

        composeRule.activityRule.scenario.recreate()

        waitForDiagnosticSurface()
        composeRule.onNodeWithText("Profile.kt:42").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostic_input").performScrollTo().assertTextContains(input)
    }

    @Test
    fun reopensAndClearsTheSavedHistory() {
        val input = "java.lang.IllegalArgumentException: invalid page"
        analyze(input)
        composeRule.onNodeWithTag("clear_input").performScrollTo().performClick()
        composeRule.onNodeWithTag("analysis_result").assertDoesNotExist()

        composeRule.onAllNodesWithTag("history_entry")[0].performScrollTo().performClick()
        waitForDiagnosticSurface()
        composeRule.onNodeWithTag("analysis_heading").assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostic_input").performScrollTo().assertTextContains(input)
        composeRule.onNodeWithTag("analysis_result").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag("clear_history").performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("history_empty").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.history_empty))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("history_entry").assertDoesNotExist()
    }

    @Test
    fun searchesAndFiltersHistoryAndOpensAMatchingDiagnosis() {
        val first = "java.lang.NullPointerException: café"
        analyze(first)
        composeRule.onNodeWithTag("clear_input").performScrollTo().performClick()
        analyze("java.lang.IllegalStateException: café")

        composeRule.onNodeWithTag("history_query").performScrollTo().performTextInput("CAFE")
        composeRule.onNodeWithTag("history_category").performScrollTo().performClick()
        composeRule.onNodeWithTag("history_category_NULL_REFERENCE").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("history_entry").fetchSemanticsNodes().size == 1
        }
        composeRule.onNodeWithTag("clear_history").assertDoesNotExist()
        composeRule.onNodeWithTag("history_entry").performScrollTo().performClick()
        composeRule.onNodeWithTag("diagnostic_input").performScrollTo().assertTextContains(first)

        composeRule.onNodeWithTag("history_query").performScrollTo().performTextReplacement("sin coincidencia")
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("history_no_matches").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("history_no_matches").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("history_empty").assertDoesNotExist()
        composeRule.onNodeWithTag("history_reset_filters").performScrollTo().performClick()
        assertEquals(2, composeRule.onAllNodesWithTag("history_entry").fetchSemanticsNodes().size)
    }

    @Test
    fun deletesOnlyTheConfirmedHistoryEntry() {
        analyze("java.lang.NullPointerException: remove this")
        composeRule.onNodeWithTag("clear_input").performScrollTo().performClick()
        analyze("java.lang.IllegalStateException: keep this")
        composeRule.onNodeWithTag("history_query").performScrollTo().performTextInput("remove this")
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("history_delete_entry").fetchSemanticsNodes().size == 1
        }

        composeRule.onNodeWithTag("history_delete_entry").performScrollTo().performClick()
        composeRule.onNodeWithTag("history_cancel_delete").performClick()
        composeRule.onNodeWithTag("history_entry").assertExists()
        composeRule.onNodeWithTag("history_delete_entry").performScrollTo().performClick()
        composeRule.onNodeWithTag("history_confirm_delete").performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("history_no_matches").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("history_reset_filters").performScrollTo().performClick()
        composeRule.onNode(hasTestTag("history_entry") and hasText("keep this")).assertExists()
    }

    @Test
    fun unknownTextIsPresentedAsUnclassified() {
        analyze("La pantalla se queda en blanco sin una traza")
        val title = composeRule.activity.getString(R.string.category_unknown)
        composeRule.onNode(hasText(title) and hasAnyAncestor(hasTestTag("analysis_result")))
            .performScrollTo().assertIsDisplayed()
    }

    @Test
    fun canLoadAndAnalyzeTheBundledExample() {
        composeRule.onNodeWithTag("load_example").performScrollTo().performClick()
        composeRule.onNodeWithTag("analyze_input").performScrollTo().performClick()
        waitForDiagnosticSurface()

        composeRule.onNodeWithText("Profile.kt:42").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun reportOptionsExcludeTheOriginalTraceByDefaultEachTime() {
        analyze("java.lang.IllegalStateException: export example")
        composeRule.onNodeWithTag("export_diagnostic").performScrollTo().performClick()
        composeRule.onNodeWithTag("export_include_trace").assertIsOff().performClick().assertIsOn()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.export_cancel)).performClick()

        composeRule.onNodeWithTag("export_diagnostic").performScrollTo().performClick()
        composeRule.onNodeWithTag("export_include_trace").assertIsOff()
    }

    @Test
    fun sharesTheReportAsPlainTextAndIncludesTheFullTraceOnlyWhenSelected() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw AssumptionViolatedException("Intercepting chooser intents requires API 26")
        }
        val trace = "java.lang.IllegalStateException: share example\n" +
            "\tat example.Screen.render(Screen.kt:42)\n" +
            "\tat example.Caller.open(Caller.kt:17)"
        analyze(trace)

        val chooser = AtomicReference<Intent>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_CHOOSER) return null
                chooser.set(intent)
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            fun shareReport(): String {
                composeRule.onNodeWithTag("share_diagnostic_text").performClick()
                val launch = checkNotNull(chooser.getAndSet(null))
                val send = checkNotNull(IntentCompat.getParcelableExtra(launch, Intent.EXTRA_INTENT, Intent::class.java))
                assertEquals(Intent.ACTION_SEND, send.action)
                assertEquals("text/plain", send.type)
                assertFalse(send.hasExtra(Intent.EXTRA_STREAM))
                return checkNotNull(send.getStringExtra(Intent.EXTRA_TEXT))
            }

            composeRule.onNodeWithTag("export_diagnostic").performScrollTo().performClick()
            val defaultReport = shareReport()
            assertTrue(defaultReport.contains("share example"))
            assertFalse(defaultReport.contains("Caller.kt:17"))

            composeRule.onNodeWithTag("export_diagnostic").performScrollTo().performClick()
            composeRule.onNodeWithTag("export_include_trace").assertIsOff().performClick()
            assertTrue(shareReport().contains(trace))
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }

    private fun analyze(input: String) {
        composeRule.onNodeWithTag("diagnostic_input").performScrollTo().performTextInput(input)
        composeRule.onNodeWithTag("analyze_input").performScrollTo().performClick()
        waitForDiagnosticSurface()
    }

    @Test
    fun a2uiActionsExpandAndCollapseCausesAndTheOriginalFragment() {
        val input = "java.lang.RuntimeException: outer wrapper\n" +
            "Caused by: java.lang.NullPointerException: missing value"
        analyze(input)
        composeRule.onNodeWithText("outer wrapper").assertDoesNotExist()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.a2ui_show_causes))
            .performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("outer wrapper").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("outer wrapper").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.a2ui_hide_causes))
            .performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("outer wrapper").fetchSemanticsNodes().isEmpty()
        }

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.a2ui_show_source))
            .performScrollTo().performClick()
        val original = hasText(input) and hasAnyAncestor(hasTestTag("analysis_result"))
        composeRule.waitUntil(10_000) { composeRule.onAllNodes(original).fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNode(original).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.a2ui_hide_source))
            .performScrollTo().performClick()
        composeRule.waitUntil(10_000) { composeRule.onAllNodes(original).fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun a2uiChecklistSurvivesComponentUpdatesAndActivityRecreation() {
        analyze("java.lang.RuntimeException: outer\nCaused by: java.lang.NullPointerException: missing")
        composeRule.onAllNodesWithTag("diagnostic_checkbox")[0].performScrollTo().performClick().assertIsOn()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.a2ui_show_causes))
            .performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText(composeRule.activity.getString(R.string.a2ui_hide_causes))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithTag("diagnostic_checkbox")[0].performScrollTo().assertIsOn()

        composeRule.activityRule.scenario.recreate()
        waitForDiagnosticSurface()

        composeRule.onAllNodesWithTag("diagnostic_checkbox")[0].performScrollTo().assertIsOn()
    }

    @Test
    fun aNewAnalysisWithIdenticalTextResetsTheA2uiChecklist() {
        analyze("java.lang.NullPointerException: same input")
        composeRule.onAllNodesWithTag("diagnostic_checkbox")[0].performScrollTo().performClick().assertIsOn()

        composeRule.onNodeWithTag("analyze_input").performScrollTo().performClick()
        composeRule.waitUntil(10_000) {
            val checks = composeRule.onAllNodesWithTag("diagnostic_checkbox").fetchSemanticsNodes()
            checks.size == 3 && checks.all { it.config[SemanticsProperties.ToggleableState] == ToggleableState.Off }
        }
        composeRule.onAllNodesWithTag("diagnostic_checkbox")[0].performScrollTo().assertIsOff()
    }

    private fun waitForDiagnosticSurface() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("a2ui_surface_ready").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag("history_entry").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
