package com.arcanatable.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.performClick
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

    private fun homeShows(name: String) {
        UiTestSupport.settle(rule)
        rule.onNodeWithTag("home-play").assertIsDisplayed()
        rule.onNodeWithTag("dev-goose-banner").assertIsDisplayed()
        UiTestSupport.screenshot(rule.activity, "home-$name")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun phonePortrait() = homeShows("phone-portrait")
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phoneLandscape() = homeShows("phone-landscape")
    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi") fun foldableCoverScreen() = homeShows("foldable-cover")
    @Test @Config(sdk = [34], qualifiers = "w673dp-h841dp-port-xhdpi") fun foldableUnfolded() = homeShows("foldable-unfolded")

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun multiplayerOnlyShowsUnifiedLobbyRoutes() {
        UiTestSupport.settle(rule)
        rule.onNodeWithTag("home-play").performClick()
        rule.onNodeWithTag("play-wifi-option").performClick()
        rule.onNodeWithText("Host online").assertExists()
        rule.onNodeWithText("Join online").assertExists()
        rule.onNodeWithText("Host on Wi-Fi").assertExists()
        rule.onNodeWithText("Join on Wi-Fi").assertExists()
        rule.onNodeWithText("Host 2–4 players online").assertDoesNotExist()
        rule.onNodeWithText("HOST ONLINE LOBBY").assertExists()
        rule.onNodeWithText("Join online").performClick()
        rule.onNodeWithText("JOIN ONLINE LOBBY").assertExists()
    }
}
