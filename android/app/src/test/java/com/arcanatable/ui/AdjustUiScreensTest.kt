package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
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

    private fun options(name: String, exerciseControls: Boolean = false) {
        val showNotice = mutableStateOf(false)
        val actions = mutableListOf<String>()
        rule.setContent {
            OptionsPage(
                darkMode = true, timerVisible = true, backgroundAlertsEnabled = false, showEngineStatus = showNotice.value, followActivePlayer = true,
                connectionLabel = "Argentum · Connected", pingResult = null, versionLabel = "build 99", diagnosticCount = 12,
                reducedSwipeMotion = false, cardSizeScale = 1f,
                onOpenLayoutLab = {}, onToggleTheme = {}, onToggleTimerVisibility = {}, onToggleBackgroundAlerts = {}, onShowEngineStatus = { showNotice.value = it },
                onFollowActivePlayer = {}, onReducedMotion = {}, onCardSizeScale = {},
                quietOpponentTurns = false, onQuietOpponentTurns = {}, onTestConnection = { actions += "test" }, onReconnect = if (exerciseControls) ({ actions += "reconnect" }) else null,
                onViewDiagnostics = {}, onExportDiagnostics = {}, onWhatsNew = {}, onClearAllData = {}, onDismiss = {}
            )
        }
        UiTestSupport.settle(rule, 300)
        UiTestSupport.screenshot(rule.activity, "options-$name")
        if (exerciseControls) {
            val switch = rule.onNodeWithTag("engine-processing-toggle")
            switch.performScrollTo()
            switch.assertIsOff()
            switch.performClick()
            // UiTestSupport.settle disables automatic Compose frame advancement for Robolectric.
            // Drive a frame so the hoisted state reaches the Switch before asserting.
            UiTestSupport.settle(rule, 200)
            switch.assertIsOn()
            rule.onNodeWithTag("diagnostics-test-connection").performScrollTo().performClick()
            rule.onNodeWithTag("diagnostics-reconnect").performScrollTo().performClick()
            assertEquals(listOf("test", "reconnect"), actions)
        }
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun phonePortraitAdjust() = adjust("phone-portrait")
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phoneLandscapeAdjust() = adjust("phone-landscape")
    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi") fun foldCoverAdjust() = adjust("fold-cover")
    @Test @Config(sdk = [34], qualifiers = "w673dp-h841dp-port-xhdpi") fun foldUnfoldedAdjust() = adjust("fold-unfolded")
    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun phonePortraitOptions() = options("phone-portrait")
    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi") fun foldCoverOptions() = options("fold-cover", exerciseControls = true)
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun landscapeOptions() = options("landscape")
}
