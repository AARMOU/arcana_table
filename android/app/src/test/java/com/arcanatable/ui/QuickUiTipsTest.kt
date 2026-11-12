package com.arcanatable.ui

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.arcanatable.MainActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class QuickUiTipsTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val prefs get() = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("arcana_options", Context.MODE_PRIVATE)

    @Before fun resetOptOut() {
        prefs.edit().remove("quick_ui_tips_disabled").remove("tutorial_offer_seen").commit()
        rule.mainClock.autoAdvance = false
    }

    @Test fun notNowDismissesTheFirstLaunchOfferAcrossRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            UiTestSupport.settle(rule)
            rule.onNodeWithText("Welcome to Arcana Table").assertIsDisplayed()
            scenario.onActivity { UiTestSupport.screenshot(it, "build122-quick-tips-folded") }
            rule.onNodeWithText("NOT NOW").performClick()
            UiTestSupport.settle(rule)
            assertTrue(rule.onAllNodesWithTag("quick-ui-tips").fetchSemanticsNodes().isEmpty())
            assertTrue(prefs.getBoolean("tutorial_offer_seen", false))
            assertTrue(prefs.getBoolean("quick_ui_tips_disabled", false))
            scenario.recreate()
            UiTestSupport.settle(rule)
            assertTrue(rule.onAllNodesWithTag("quick-ui-tips").fetchSemanticsNodes().isEmpty())
            assertTrue(UiTestSupport.homeShown(rule))
        }
    }

    @Test fun openTutorialsShowsTheOfflineHubAndRemembersTheOffer() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            UiTestSupport.settle(rule)
            rule.onNodeWithText("OPEN TUTORIALS").performClick()
            UiTestSupport.settle(rule)
            rule.onNodeWithText("Learn to play").assertIsDisplayed()
            assertTrue(prefs.getBoolean("tutorial_offer_seen", false))
            scenario.recreate()
            UiTestSupport.settle(rule)
            assertTrue(rule.onAllNodesWithTag("quick-ui-tips").fetchSemanticsNodes().isEmpty())
            assertTrue(UiTestSupport.homeShown(rule))
        }
    }

    @Test
    @Config(qualifiers = "w891dp-h411dp-land-xxhdpi")
    fun popupActionsRemainVisibleInLandscape() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            UiTestSupport.settle(rule)
            rule.onNodeWithText("NOT NOW").assertIsDisplayed()
            rule.onNodeWithText("OPEN TUTORIALS").assertIsDisplayed()
            scenario.onActivity { UiTestSupport.screenshot(it, "build122-quick-tips-landscape") }
        }
    }
}
