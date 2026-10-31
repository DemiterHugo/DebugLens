package io.github.demiterhugo.debuglens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launchesWithHiltAndShowsHome() {
        assertEquals("io.github.demiterhugo.debuglens", composeRule.activity.packageName)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_empty_title))
            .assertIsDisplayed()
    }

    @Test
    fun aboutRemainsOpenAfterActivityRecreationAndCanBeDismissed() {
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_about))
            .performClick()

        composeRule.activityRule.scenario.recreate()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_about_description))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.action_close))
            .performClick()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.home_about_description))
            .assertDoesNotExist()
    }
}
