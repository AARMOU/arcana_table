package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.arcanatable.ArgentumSeatChip
import com.arcanatable.ArgentumSeatTab
import com.arcanatable.CardFace
import com.arcanatable.CardZoomController
import com.arcanatable.CardZoomOverlay
import com.arcanatable.GameCard
import com.arcanatable.LocalCardZoom
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Real pointer events exercise the components independently of the server-fixture layout tests. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class ArgentumGestureUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun physicalTapInvokesScrollableSeatTab() {
        var clicks = 0
        rule.setContent {
            MaterialTheme {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    ArgentumSeatTab(ArgentumSeatChip("GESTURE TEST", 40, false, false, false),
                        null, null, false, { clicks++ })
                }
            }
        }
        UiTestSupport.settle(rule)
        rule.onNodeWithText("GESTURE TEST").performClick()
        UiTestSupport.settle(rule)
        assertEquals(1, clicks)
    }

    private fun preview() {
        val zoom = CardZoomController().apply { enabled = true }
        rule.setContent {
            CompositionLocalProvider(LocalCardZoom provides zoom) {
                MaterialTheme {
                    Box {
                        CardFace(GameCard(1, "Gesture fixture", "Creature", Color.Green),
                            Modifier.width(100.dp).testTag("gesture-card"), {}, {})
                        CardZoomOverlay(zoom)
                    }
                }
            }
        }
        UiTestSupport.settle(rule)
        rule.onNodeWithTag("gesture-card").performTouchInput { longClick() }
        UiTestSupport.settle(rule, 800)
        assertEquals("Gesture fixture", zoom.card?.name)
        assertEquals(1, rule.onAllNodesWithTag("card-zoom").fetchSemanticsNodes().size)
        rule.onNodeWithTag("card-zoom").performClick()
        UiTestSupport.settle(rule, 600)
        assertTrue(rule.onAllNodesWithTag("card-zoom").fetchSemanticsNodes().isEmpty())
        assertNull(zoom.card)
    }

    @Test fun physicalHoldAndTapDismissPreview() = preview()
    @Test @Config(qualifiers = "w891dp-h411dp-land-xxhdpi") fun landscapePreviewGesture() = preview()
    @Test @Config(qualifiers = "w344dp-h882dp-port-xxhdpi") fun foldCoverPreviewGesture() = preview()
    @Test @Config(qualifiers = "w673dp-h841dp-port-xhdpi") fun foldUnfoldedPreviewGesture() = preview()
}
