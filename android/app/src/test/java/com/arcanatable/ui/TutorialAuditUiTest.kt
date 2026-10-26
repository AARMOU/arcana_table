package com.arcanatable.ui

import android.content.Context
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.arcanatable.MainActivity
import com.arcanatable.TableController
import com.arcanatable.TutorialPracticeScenarios
import com.arcanatable.TutorialTargets
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Walks every tutorial chapter in the real app the way a player does (Learn → chapter →
 * SHOW ON SCREEN, or PRACTICE for the engine drills) and checks each step:
 *  - the coach card is visible and on top of whatever screen the step needs;
 *  - the step's screen actually opened;
 *  - the highlighted control exists on screen (or the step deliberately has none);
 *  - the step can be finished by its own button or the highlighted control.
 * Writes tutorial-audit.txt next to the screenshots, one line per step.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class TutorialAuditUiTest {
    @get:Rule val rule = createEmptyComposeRule()

    @Before fun skipFirstLaunchOffer() {
        ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("arcana_options", Context.MODE_PRIVATE)
            .edit().putBoolean("tutorial_offer_seen", true).putBoolean("quick_ui_tips_disabled", true).commit()
        rule.mainClock.autoAdvance = false
    }

    private val report = StringBuilder()
    private val problems = mutableListOf<String>()

    private fun screenOf(t: TableController): String = when {
        t.tutorialHubOpen -> "hub"
        t.diagnosticViewerOpen -> "diagnostics"
        t.optionsOpen -> "settings"
        t.playSheetOpen -> "setup"
        t.deckPickerOpen -> "decks"
        t.homeOpen -> "home"
        t.stackOpen -> "stack"
        t.topMenuExpanded -> "menu"
        t.argentumEnabled || t.players.isNotEmpty() -> "board"
        else -> "?"
    }

    private fun coachShown() = rule.onAllNodes(hasTestTag("tutorial-coach-tip")).fetchSemanticsNodes().isNotEmpty()
    private fun nextButton() = hasText("NEXT") and hasAnyAncestor(hasTestTag("tutorial-coach-tip"))

    private fun audit(chapter: String) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            UiTestSupport.settle(rule)
            lateinit var t: TableController
            scenario.onActivity { t = checkNotNull(it.tableController) }
            val practice = chapter in TutorialPracticeScenarios.supported
            if (practice) {
                t.startTutorialPractice(chapter)
                UiTestSupport.awaitReal(rule, 90_000, "practice game for $chapter") { !t.tutorialLoading && t.argentumSnapshot != null }
            } else t.startTutorialCoach(chapter)
            UiTestSupport.settle(rule, 800)
            val lesson = checkNotNull(t.tutorialLesson) { "lesson $chapter did not start" }
            var guard = 0
            while (t.tutorialLesson != null && guard++ < lesson.steps.size + 3) {
                val index = t.tutorialStepIndex
                val step = lesson.steps[index]
                UiTestSupport.settle(rule, 600)
                val bounds = step.target?.let { TutorialTargets.bounds[it] }
                val targetOk = step.target == null || (bounds != null && bounds.width > 1f && bounds.height > 1f)
                val screen = screenOf(t)
                val wantScreen = step.screen.ifBlank { "board" }
                val screenOk = screen == wantScreen || (wantScreen == "layout" && screen == "settings") || (wantScreen == "menu" && screen == "board")
                val coach = coachShown()
                val hasNext = rule.onAllNodes(nextButton()).fetchSemanticsNodes().isNotEmpty()
                val name = "tutorial-$chapter-${"%02d".format(index + 1)}-${step.id}"
                scenario.onActivity { UiTestSupport.screenshot(it, name) }
                // Advance the way a player would: NEXT if offered, otherwise the step's own control.
                var how: String
                if (hasNext) {
                    rule.onNode(nextButton()).performClick()
                    how = "next"
                } else {
                    how = "gated-${step.actionKind}"
                    when (step.actionKind) {
                        "tap", "hold" -> t.tutorialSignal(step.actionKind, step.target.orEmpty().replace(":any", ":Forest"))
                        else -> t.tutorialAdvance()
                    }
                }
                UiTestSupport.settle(rule, 400)
                val moved = t.tutorialLesson == null || t.tutorialStepIndex != index
                if (!moved) { how += "-STUCK"; t.tutorialAdvance(); UiTestSupport.settle(rule, 300) }
                val flags = buildList {
                    if (!coach) add("NO-COACH")
                    if (!screenOk) add("SCREEN(want=$wantScreen got=$screen)")
                    if (!targetOk) add("NO-TARGET(${step.target})")
                    if (!moved) add("STUCK")
                }
                val line = "$chapter #${index + 1} ${step.id} [${step.actionKind}] screen=$screen target=${step.target} -> $how ${if (flags.isEmpty()) "OK" else flags.joinToString(" ")}"
                report.appendLine(line)
                if (flags.isNotEmpty()) problems += line
            }
            if (t.tutorialLesson != null) { problems += "$chapter: never finished"; report.appendLine("$chapter: NEVER FINISHED") }
            if (practice) t.stopTutorial()
        }
    }

    private fun writeReport(name: String) {
        val dir = File(System.getProperty("arcana.screenshots") ?: "build/screenshots").apply { mkdirs() }
        File(dir, "$name.txt").writeText(report.toString() + "\nPROBLEMS: ${problems.size}\n" + problems.joinToString("\n"))
    }

    private fun run(name: String, chapters: List<String>) {
        try { chapters.forEach { c -> runCatching { audit(c) }.onFailure { problems += "$c: CRASH ${it.javaClass.simpleName}: ${it.message?.take(300)}"; report.appendLine("$c: CRASH $it") } } }
        finally { writeReport(name) }
        assertTrue("Tutorial problems:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    @Test fun appControlChapters() = run("tutorial-audit-app",
        listOf("home", "setup", "table", "quick-actions", "opponents", "decks", "settings", "tools", "mobile", "help"))

    @Test fun learnMagicChapters() = run("tutorial-audit-magic",
        listOf("cards", "mana", "turns", "commander", "zones", "unavailable-actions"))

    @Test fun practiceChapters() = run("tutorial-audit-practice", listOf("first-turn", "opening-hand", "stack", "combat"))
}
