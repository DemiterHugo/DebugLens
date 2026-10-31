package io.github.demiterhugo.debuglens

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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun clearPreviousHistory() {
        if (composeRule.onAllNodesWithTag("clear_history").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag("clear_history").performScrollTo().performClick()
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
    fun reopensAndClearsTheTemporaryHistory() {
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
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.history_empty))
            .performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("history_entry").assertDoesNotExist()
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
            composeRule.onAllNodesWithTag("a2ui_surface_ready").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
