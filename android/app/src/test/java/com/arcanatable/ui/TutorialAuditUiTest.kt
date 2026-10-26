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
import com.arcanatable.TutorialCoachState
import com.arcanatable.chooseArgentumLegalAction
import com.arcanatable.sendArgentumPriority
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
        t.argentumMulliganPrompt || t.argentumBottomCardIds != null -> "mulligan"
        t.layoutLabOpen -> "layout"
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
            // The same entry point as START LESSON in Learn.
            t.startTutorial(chapter)
            if (t.tutorialPracticeActive) UiTestSupport.awaitReal(rule, 120_000, "practice game for $chapter") {
                !t.tutorialLoading && (t.argentumSnapshot != null || t.argentumMulliganPrompt)
            }
            val practice = t.tutorialPracticeActive
            UiTestSupport.settle(rule, 1_000)
            val lesson = checkNotNull(t.tutorialLesson) { "lesson $chapter did not start" }
            var guard = 0
            while (t.tutorialLesson != null && guard++ < lesson.steps.size + 3) {
                val index = t.tutorialStepIndex
                val step = lesson.steps[index]
                UiTestSupport.settle(rule, 600)
                val top = topWindow()
                val resolved = step.target?.let { TutorialTargets.resolve(it, top) }
                val bounds = resolved?.let { TutorialTargets.bounds[it] }
                // The control must be on the window the player is looking at, not hidden behind it.
                val targetOk = step.target == null || (bounds != null && bounds.width > 1f && bounds.height > 1f)
                val coachCount = rule.onAllNodes(hasTestTag("tutorial-coach-tip")).fetchSemanticsNodes().size
                val screen = screenOf(t)
                val wantScreen = step.screen.ifBlank { "board" }
                val screenOk = screen == wantScreen
                val coach = coachShown()
                val hasNext = rule.onAllNodes(nextButton()).fetchSemanticsNodes().isNotEmpty()
                val coachOnTop = TutorialCoachState.layers.isNotEmpty() && coachInTopWindow(top)
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
                        "tap", "hold" -> t.tutorialSignal(step.actionKind, resolved ?: step.target.orEmpty())
                        // Play the drill for real: land, mana, cast, pass. Anything else is forced.
                        "event" -> if (!playFor(t, step.event.orEmpty())) { how += "-forced"; t.tutorialAdvance() }
                    }
                    UiTestSupport.awaitReal(rule, 20_000, "engine after ${step.id}") { !t.argentumAwaitingUpdate }
                }
                UiTestSupport.settle(rule, 400)
                val moved = t.tutorialLesson == null || t.tutorialStepIndex != index
                if (!moved) { how += "-STUCK"; t.tutorialAdvance(); UiTestSupport.settle(rule, 300) }
                val flags = buildList {
                    if (!coach) add("NO-COACH")
                    if (coachCount > 1) add("DUPLICATE-COACH($coachCount)")
                    if (coach && !coachOnTop) add("COACH-BEHIND-WINDOW")
                    if (!screenOk) add("SCREEN(want=$wantScreen got=$screen)")
                    if (!targetOk) add("NO-TARGET(${step.target})")
                    if (!moved) add("STUCK")
                }
                val line = "$chapter #${index + 1} ${step.id} [${step.actionKind}] screen=$screen target=${step.target} -> $how ${if (flags.isEmpty()) "OK" else flags.joinToString(" ")}"
                report.appendLine(line)
                if (flags.isNotEmpty()) problems += line
            }
            if (t.tutorialLesson != null) { problems += "$chapter: never finished"; report.appendLine("$chapter: NEVER FINISHED") }
            if (t.tutorialLesson != null || practice) t.stopTutorial()
        }
    }

    /** The topmost app window (the activity, or the dialog/sheet/popup on top of it). */
    private fun topWindow(): android.view.View {
        val global = Class.forName("android.view.WindowManagerGlobal")
        val instance = global.getMethod("getInstance").invoke(null)
        @Suppress("UNCHECKED_CAST")
        val views = (global.getDeclaredField("mViews").apply { isAccessible = true }.get(instance) as List<android.view.View>)
        return views.last { it.isAttachedToWindow && it.width > 0 }.rootView
    }

    /** True when the coach card is drawn in [top], the window the player is looking at. */
    private fun coachInTopWindow(top: android.view.View): Boolean = TutorialCoachState.drawnIn === top

    /** Does the real move a drill step waits for, through the same calls the table's buttons make. */
    private fun playFor(t: TableController, event: String): Boolean {
        val actions = t.argentumSnapshot?.legalActions.orEmpty()
        fun pick(match: (org.json.JSONObject) -> Boolean): Boolean {
            val info = actions.firstOrNull(match) ?: return false
            t.chooseArgentumLegalAction(info); return true
        }
        fun type(i: org.json.JSONObject) = i.optJSONObject("action")?.optString("type").orEmpty()
        fun desc(i: org.json.JSONObject) = i.optString("description")
        return when (event) {
            "LandPlayed" -> pick { type(it) != "PassPriority" && "Forest" in desc(it) && "Add" !in desc(it) }
            "ManaAdded" -> pick { "Add {G}" in desc(it) || type(it).contains("Mana") }
            "SpellCast" -> pick { type(it) == "CastSpell" && ("Elvish Mystic" in desc(it) || "Giant Growth" in desc(it)) }
            "SpellResolved", "PriorityPassed", "TurnAdvanced" -> { t.sendArgentumPriority(); true }
            else -> false
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
