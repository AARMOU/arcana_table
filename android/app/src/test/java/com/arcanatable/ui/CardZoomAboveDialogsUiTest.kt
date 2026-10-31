package com.arcanatable.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.arcanatable.CardZoneDialog
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
 * Holding a card inside any dialog (graveyard/exile viewer here; also library search, stack, log,
 * token picker, preview) must show the table-wide zoom *above* that dialog. As a Popup the zoom was a
 * sub-window of the activity and drew underneath every dialog; it must be its own top-level
 * application window, added after the screen it was opened from.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class CardZoomAboveDialogsUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    /** Window params in the order the windows were added (later = higher among app windows). */
    @Suppress("UNCHECKED_CAST")
    private fun windowParams(): List<WindowManager.LayoutParams> {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val field = global.javaClass.getDeclaredField("mParams").apply { isAccessible = true }
        return (field.get(global) as List<WindowManager.LayoutParams>).toList()
    }

    @Test fun holdingACardInTheGraveyardViewerZoomsAboveTheViewer() {
        val zoom = CardZoomController().apply { enabled = true }
        rule.setContent {
            CompositionLocalProvider(LocalCardZoom provides zoom) {
                MaterialTheme {
                    Box {
                        CardZoneDialog("GRAVEYARD", "You", listOf(GameCard(7, "Zone fixture", "Creature", Color.Green)), onDismiss = {}, onPreview = {})
                        CardZoomOverlay(zoom)
                    }
                }
            }
        }
        UiTestSupport.settle(rule)
        val before = windowParams()
        val viewerIndex = before.indexOfLast { it.type in WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW..WindowManager.LayoutParams.LAST_APPLICATION_WINDOW }

        // A card with no image shows its name; holding there holds the card.
        rule.onNodeWithText("Zone fixture").performTouchInput { longClick() }
        UiTestSupport.settle(rule, 800)

        assertEquals("Zone fixture", zoom.card?.name)
        assertEquals(1, rule.onAllNodesWithTag("card-zoom").fetchSemanticsNodes().size)
        val after = windowParams()
        assertEquals("Holding a card opens exactly one new window", before.size + 1, after.size)
        val zoomWindow = after.last()
        assertTrue("Zoom must be a top-level app window, not a sub-window under the dialog (type ${zoomWindow.type})",
            zoomWindow.type in WindowManager.LayoutParams.FIRST_APPLICATION_WINDOW..WindowManager.LayoutParams.LAST_APPLICATION_WINDOW)
        assertTrue("Zoom window must be added after the viewer", after.lastIndex > viewerIndex)

        rule.onNodeWithTag("card-zoom").performClick()
        UiTestSupport.settle(rule, 600)
        assertTrue(rule.onAllNodesWithTag("card-zoom").fetchSemanticsNodes().isEmpty())
        assertNull(zoom.card)
    }
}
