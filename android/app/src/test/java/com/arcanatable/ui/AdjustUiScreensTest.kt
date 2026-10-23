package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.arcanatable.AdjustUiPage
import com.arcanatable.HandArrangement
import com.arcanatable.OptionsPage
import com.arcanatable.argentumQuickActionIds
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Captures Adjust UI and Options at the four sizes used by the home and game UI tests. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AdjustUiScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun adjust(name: String) {
        rule.setContent {
            AdjustUiPage("PORTRAIT", 1f, {}, 1f, {}, .52f, {}, 1f, {}, 1f, {}, HandArrangement.AUTO_FIT, {},
                argentumQuickActionIds, {},
                true, {}, true, {}, true, {}, true, {}, true, {}, true, {}, true, {}, true, {}, 1.5f, {}, 1f, {}, {}, {})
        }
        UiTestSupport.settle(rule, 300)
        rule.onNodeWithText("ADJUST UI · PORTRAIT").assertExists()
        UiTestSupport.screenshot(rule.activity, "adjust-ui-$name")
    }

    private fun options(name: String) {
        rule.setContent {
            OptionsPage(
                darkMode = true, timerVisible = true, backgroundAlertsEnabled = false, followActivePlayer = true,
                connectionLabel = "Argentum · Connected", pingResult = null, versionLabel = "build 99", diagnosticCount = 12,
                reducedSwipeMotion = false, cardSizeScale = 1f,
                onOpenLayoutLab = {}, onToggleTheme = {}, onToggleTimerVisibility = {}, onToggleBackgroundAlerts = {},
                onFollowActivePlayer = {}, onReducedMotion = {}, onCardSizeScale = {},
                quietOpponentTurns = false, onQuietOpponentTurns = {}, onTestConnection = {}, onReconnect = null,
                onViewDiagnostics = {}, onExportDiagnostics = {}, onWhatsNew = {}, onClearAllData = {}, onDismiss = {}
            )
        }
        UiTestSupport.settle(rule, 300)
        UiTestSupport.screenshot(rule.activity, "options-$name")
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun phonePortraitAdjust() = adjust("phone-portrait")
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phoneLandscapeAdjust() = adjust("phone-landscape")
    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi") fun foldCoverAdjust() = adjust("fold-cover")
    @Test @Config(sdk = [34], qualifiers = "w673dp-h841dp-port-xhdpi") fun foldUnfoldedAdjust() = adjust("fold-unfolded")
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun phonePortraitOptions() = options("phone-portrait")
}
