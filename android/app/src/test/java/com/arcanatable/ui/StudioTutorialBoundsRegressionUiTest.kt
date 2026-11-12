package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.arcanatable.BuildConfig
import com.arcanatable.TutorialTargets
import com.arcanatable.tutorialTarget
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Studio J11: real Compose layout regression for tutorial-highlight bounds.
 *
 * Test-only branch: no application or Claude-owned production sources modified.
 * Robolectric layout proof, not physical Fold-device acceptance.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class StudioTutorialBoundsRegressionUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @After fun clearRegistry() {
        rule.runOnIdle {
            TutorialTargets.bounds.clear()
            TutorialTargets.windows.clear()
            TutorialTargets.owners.clear()
        }
    }

    /** Studio can draw a control bigger than its parent. Highlight the full on-screen control. */
    @Test fun studioResizedControlDoesNotUseOnlyItsParentsClippedBounds() {
        assertTrue("J11 is a Studio-only contract", BuildConfig.STUDIO_ENABLED)
        val visible = mutableStateOf(true)
        val id = "j11-oversize-target"
        rule.setContent {
            Box(Modifier.fillMaxSize().padding(32.dp)) {
                Box(Modifier.size(width = 120.dp, height = 36.dp).clipToBounds()) {
                    if (visible.value) {
                        Box(
                            Modifier.requiredSize(width = 196.dp, height = 88.dp)
                                .testTag(id).tutorialTarget(id)
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag(id).fetchSemanticsNode()

        val density = rule.density.density
        rule.runOnIdle {
            val bounds = TutorialTargets.bounds[id]
            assertNotNull("J11 tutorial target must register a visible region", bounds)
            val full = bounds!!
            assertTrue("J11 width cut by 120dp parent: $full", full.width > 170f * density)
            assertTrue("J11 height cut by 36dp parent: $full", full.height > 70f * density)
            assertTrue("target must stay within its window", full.left >= -1f && full.top >= -1f)
            assertEquals(rule.activity.window.decorView.rootView, TutorialTargets.windows[id])
            assertEquals(id, TutorialTargets.resolve(id, rule.activity.window.decorView.rootView))
        }

        rule.runOnIdle { visible.value = false }
        rule.waitForIdle()
        rule.runOnIdle {
            assertNull("disposed control left a stale highlighted area", TutorialTargets.bounds[id])
            assertNull("disposed control still resolves", TutorialTargets.resolve(id, rule.activity.window.decorView.rootView))
        }
    }

    /** A zero-size duplicate must not steal the active target or survive its disposal. */
    @Test fun collapsedDuplicateCannotStealVisibleTargetAndCannotLeaveStaleBounds() {
        val id = "j11-duplicate-target"
        val primary = mutableStateOf(true)
        val collapsed = mutableStateOf(false)
        rule.setContent {
            Column {
                if (primary.value)
                    Box(Modifier.size(96.dp, 64.dp).testTag("j11-visible").tutorialTarget(id))
                if (collapsed.value)
                    Box(Modifier.size(0.dp).testTag("j11-collapsed").tutorialTarget(id))
            }
        }
        rule.waitForIdle()
        rule.onNodeWithTag("j11-visible").fetchSemanticsNode()
        val original = rule.runOnIdle { TutorialTargets.bounds[id] }
        assertNotNull(original)
        assertTrue(original!!.width > 1f && original.height > 1f)

        rule.runOnIdle { collapsed.value = true }
        rule.waitForIdle()
        rule.runOnIdle {
            assertEquals("collapsed duplicate replaced a visible target", original, TutorialTargets.bounds[id])
            assertEquals(id, TutorialTargets.resolve(id, rule.activity.window.decorView.rootView))
        }

        rule.runOnIdle { primary.value = false }
        rule.waitForIdle()
        rule.runOnIdle {
            assertNull("hidden original left stale highlighted bounds", TutorialTargets.bounds[id])
            assertNull("only collapsed duplicate must not resolve", TutorialTargets.resolve(id, rule.activity.window.decorView.rootView))
        }
    }

    /** A dialog behind the active one may not supply the current coach highlight. */
    @Test fun targetFromDifferentWindowNeverResolvesOnCurrentWindow() {
        val id = "j11-other-window"
        rule.setContent {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.size(72.dp).testTag(id).tutorialTarget(id))
            }
        }
        rule.waitForIdle()
        val hostRoot = rule.activity.window.decorView.rootView
        rule.runOnIdle {
            assertEquals(id, TutorialTargets.resolve(id, hostRoot))
            val otherWindow = android.view.View(rule.activity)
            assertNull("background-window target incorrectly resolved in foreground", TutorialTargets.resolve(id, otherWindow))
        }
    }
}
