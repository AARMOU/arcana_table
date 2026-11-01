package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import com.arcanatable.ArgentumOpeningHandScreen
import com.arcanatable.CardZoomController
import com.arcanatable.CardZoomOverlay
import com.arcanatable.GameCard
import com.arcanatable.LocalCardZoom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Holding a card in the Argentum opening hand used to go to the table-wide zoom, whose popup belongs
 * to the activity window: it showed *behind* the full-screen hand dialog and was still up after
 * Keep Hand. Wired like TableScreen: the table zoom and its overlay are always present, and the hand
 * screen is composed only while the prompt is up.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class OpeningHandZoomUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val card = GameCard(1, "Hand fixture", "Creature", Color.Green)
    private var showHand by mutableStateOf(true)

    private fun setContent(tableZoom: CardZoomController) = rule.setContent {
        CompositionLocalProvider(LocalCardZoom provides tableZoom) {
            MaterialTheme {
                Box {
                    if (showHand) ArgentumOpeningHandScreen(
                        cards = listOf(card), title = "Opening hand", subtitle = "Test hand",
                        selectable = false, selection = emptySet(), onToggle = {},
                        primaryLabel = "KEEP 1", primaryEnabled = true, onPrimary = { showHand = false },
                        secondaryLabel = "MULLIGAN", secondaryEnabled = true, onSecondary = {},
                    )
                    CardZoomOverlay(tableZoom)
                }
            }
        }
    }

    @Test fun holdingAHandCardZoomsInsideTheDialogAndKeepClosesIt() {
        val tableZoom = CardZoomController().apply { enabled = true }
        setContent(tableZoom)
        UiTestSupport.settle(rule)

        rule.onNodeWithTag("opening-hand-card").performTouchInput { longClick() }
        UiTestSupport.settle(rule, 800)
        assertNull("The hold must not reach the table zoom, which draws behind the dialog", tableZoom.card)
        assertEquals(1, rule.onAllNodesWithTag("card-zoom").fetchSemanticsNodes().size)

        // Keep Hand closes the prompt (TableOverlays sets argentumMulliganPrompt = false) with the preview still up.
        showHand = false
        UiTestSupport.settle(rule, 600)
        assertTrue("No preview may outlive the opening hand", rule.onAllNodesWithTag("card-zoom").fetchSemanticsNodes().isEmpty())
        assertNull(tableZoom.card)
    }

    @Test fun openingTheHandClearsAPreviewLeftOnTheTable() {
        val tableZoom = CardZoomController().apply { enabled = true }
        tableZoom.hold(card)
        tableZoom.release(moved = false) // a still release keeps the table preview up
        showHand = false
        setContent(tableZoom)
        UiTestSupport.settle(rule)
        assertEquals("Hand fixture", tableZoom.card?.name)

        showHand = true
        UiTestSupport.settle(rule, 600)
        assertNull("A table preview would sit hidden behind the hand dialog", tableZoom.card)
    }
}
