package com.arcanatable.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.performClick
import com.arcanatable.ArcanaLesson
import com.arcanatable.ArcanaLessonStep
import com.arcanatable.TutorialCoachOverlay
import com.arcanatable.TutorialHubDialog
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Native tutorial smoke tests on phone portrait and both Fold display classes. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TutorialScreensTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
    fun tutorialHubLoadsBundledChaptersOffline() {
        rule.setContent { TutorialHubDialog(onDismiss = {}) }
        UiTestSupport.settle(rule, 300)
        rule.onNodeWithText("Learn to play").assertExists()
        // Both the featured activity and the curriculum row have this title.
        rule.onAllNodesWithText("Your first turn").assertCountEquals(2)
        rule.onNodeWithText("PLAY THE FIRST TURN").assertExists()
        // A chapter opens to one clear way in, plus a text-only option.
        rule.onNodeWithText("Home and navigation").performClick()
        UiTestSupport.settle(rule, 300)
        rule.onNodeWithText("START LESSON").assertExists()
        rule.onNodeWithText("READ IT HERE INSTEAD").performClick()
        UiTestSupport.settle(rule, 300)
        rule.onNodeWithText("STEP 1 OF 6").assertExists()
    }

    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi")
    fun foldCoverActionStepIsGated() {
        val lesson = ArcanaLesson(
            id = "test", title = "Your first turn", section = "Quick Start",
            minutes = 4, description = "Test", steps = listOf(
                ArcanaLessonStep("land", "board", "hand-card:Forest", "LandPlayed",
                    "Play a land", "Play a Forest by tapping the actual card", "Choose Play Land", "event")
            )
        )
        rule.setContent {
            TutorialCoachOverlay(lesson = lesson, index = 0, practice = true,
                onNext = { error("A gated step cannot advance by Next") }, onSkip = {})
        }
        UiTestSupport.settle(rule, 300)
        rule.onNodeWithText("Play a land").assertExists()
        rule.onNodeWithText("EXIT").assertExists()
        rule.onNodeWithText("NEXT").assertDoesNotExist()
    }

    @Test @Config(sdk = [34], qualifiers = "w673dp-h841dp-port-xhdpi")
    fun unfoldedFoldHubUsesTheSameCurriculum() {
        rule.setContent { TutorialHubDialog(onDismiss = {}) }
        UiTestSupport.settle(rule, 300)
        rule.onNodeWithText("Learn to play").assertExists()
        rule.onNodeWithText("20 short chapters, real-control walkthroughs, and engine-backed practice games. Your saved game is never overwritten.").assertExists()
    }
}
