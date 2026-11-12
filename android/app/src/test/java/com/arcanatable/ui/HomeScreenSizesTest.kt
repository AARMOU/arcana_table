package com.arcanatable.ui

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import com.arcanatable.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Launches the real app at phone and foldable sizes and captures the home screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeScreenSizesTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun readyHome() {
        UiTestSupport.settle(rule)
        if (rule.onAllNodesWithTag("quick-ui-tips").fetchSemanticsNodes().isNotEmpty()) {
            rule.onNodeWithText("Dismiss").performClick()
            UiTestSupport.settle(rule, 300)
        }
    }

    private fun homeShows(name: String) {
        readyHome()
        rule.onNodeWithTag("home-play").assertIsDisplayed()
        rule.onNodeWithTag("test-scenarios-entry").assertIsDisplayed()
        rule.onNodeWithTag("dev-goose-banner").assertIsDisplayed()
        UiTestSupport.screenshot(rule.activity, "home-$name")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun phonePortrait() = homeShows("phone-portrait")
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phoneLandscape() = homeShows("phone-landscape")
    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi") fun foldableCoverScreen() = homeShows("foldable-cover")
    @Test @Config(sdk = [34], qualifiers = "w673dp-h841dp-port-xhdpi") fun foldableUnfolded() = homeShows("foldable-unfolded")
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun scenariosOpenFromHeaderWithoutHuntingBelowDeck() {
        readyHome()
        rule.onNodeWithTag("test-scenarios-entry").assertIsDisplayed().performClick()
        UiTestSupport.settle(rule)
        rule.onNodeWithTag("test-scenario-iron-spider-mixed-counters").assertIsDisplayed()
        rule.onNodeWithTag("test-scenario-lunar-whale-just-cast").assertExists()
        rule.onNodeWithTag("test-scenario-lunar-whale-held").assertExists()
        rule.onNodeWithTag("test-scenario-ulalek-copy").assertExists()
        rule.onNodeWithTag("test-scenario-ulalek-own-stack").assertExists()
        rule.onNodeWithTag("test-scenario-eldrazi-displacer-blink").assertExists()
        UiTestSupport.screenshot(rule.activity, "home-scenario-chooser")
    }
}
