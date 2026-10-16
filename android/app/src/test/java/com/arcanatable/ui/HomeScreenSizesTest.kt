package com.arcanatable.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        rule.onNodeWithText("PLAY WITH ARGENTUM").assertIsDisplayed()
        UiTestSupport.screenshot(rule.activity, "home-$name")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun phonePortrait() = homeShows("phone-portrait")
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phoneLandscape() = homeShows("phone-landscape")
    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi") fun foldableCoverScreen() = homeShows("foldable-cover")
    @Test @Config(sdk = [34], qualifiers = "w673dp-h841dp-port-xhdpi") fun foldableUnfolded() = homeShows("foldable-unfolded")
}
