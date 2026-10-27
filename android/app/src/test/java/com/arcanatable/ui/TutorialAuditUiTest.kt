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
import com.arcanatable.submitArgentumAction
import com.arcanatable.openArgentumCombat
import com.arcanatable.openArgentumActions
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
                val tipNodes = rule.onAllNodes(hasTestTag("tutorial-coach-tip")).fetchSemanticsNodes()
                val coachCount = tipNodes.size
                // This was not checked by the previous 160-step walkthrough: both controls
                // and the coach could exist yet the card could cover the required tap target.
                val tipRect = tipNodes.singleOrNull()?.boundsInWindow
                val coachCoversTarget = tipRect != null && bounds != null &&
                    minOf(tipRect.right, bounds.right) > maxOf(tipRect.left, bounds.left) &&
                    minOf(tipRect.bottom, bounds.bottom) > maxOf(tipRect.top, bounds.top)
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
                        "tap", "hold" -> { tapLikeThePlayer(t, step.target.orEmpty()); t.tutorialSignal(step.actionKind, resolved ?: step.target.orEmpty()) }
                        // Play the drill for real: land, mana, cast, pass, attack.
                        "event" -> {
                            val played = playFor(t, step.event.orEmpty()) { t.tutorialLesson == null || t.tutorialStepIndex != index }
                            if (!played) { how += "-forced"; t.tutorialAdvance() }
                        }
                    }
                    UiTestSupport.awaitReal(rule, 20_000, "engine after ${step.id}") { !t.argentumAwaitingUpdate }
                }
                UiTestSupport.settle(rule, 400)
                val moved = t.tutorialLesson == null || t.tutorialStepIndex != index
                // An action step the engine didn't confirm is forced on (the engine walkthrough test covers
                // those); a step that NEXT or its own control couldn't move is a real problem.
                if (!moved && step.actionKind == "event") { how += "-forced"; t.tutorialAdvance(); UiTestSupport.settle(rule, 300) }
                else if (!moved) { how += "-STUCK"; t.tutorialAdvance(); UiTestSupport.settle(rule, 300) }
                val flags = buildList {
                    if (!coach) add("NO-COACH")
                    if (coachCount > 1) add("DUPLICATE-COACH($coachCount)")
                    if (coach && !coachOnTop) add("COACH-BEHIND-WINDOW")
                    if (!screenOk) add("SCREEN(want=$wantScreen got=$screen)")
                    if (!targetOk) add("NO-TARGET(${step.target})")
                    if (coachCoversTarget) add("COACH-COVERS-TARGET(${step.target})")
                    if (!moved && step.actionKind != "event") add("STUCK")
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

    /** What the highlighted control's own handler does (the same state changes as a real tap). */
    private fun tapLikeThePlayer(t: TableController, target: String) {
        when (target) {
            "hand-handle" -> t.showHand = !t.showHand
            "stack-button" -> t.stackOpen = true
            "stack-close" -> t.stackOpen = false
            "game-menu" -> { t.topMenuSection = null; t.topMenuExpanded = true }
            "menu-game-tools" -> t.topMenuSection = "tools"
            "home-settings" -> t.optionsOpen = true
            "home-change-deck" -> t.deckPickerOpen = true
            "home-play" -> t.playSheetOpen = true
            "setting-adjust-ui" -> { t.optionsOpen = false; t.layoutLabOpen = true }
            "table-tab" -> t.showOverview = true
            "your-tab" -> { t.showOverview = false; t.selectedPlayer = t.localSeat }
            "opponent-tab" -> { t.showOverview = false; t.selectedPlayer = 1 - t.localSeat }
            else -> if (target.startsWith("hand-card:")) {
                // Tapping a card in hand opens its legal-actions sheet.
                val snap = t.argentumSnapshot
                val hand = snap?.players?.getOrNull(snap.viewingPlayer)?.hand.orEmpty()
                val name = target.removePrefix("hand-card:")
                (hand.firstOrNull { it.name == name } ?: hand.firstOrNull())?.let { t.openArgentumActions(it) }
            }
        }
        UiTestSupport.settle(rule, 300)
    }

    /**
     * Does the real move a drill step waits for, through the same calls the table's buttons make,
     * repeating passes (each only once Argentum offers one) until [moved].
     */
    private fun playFor(t: TableController, event: String, moved: () -> Boolean): Boolean {
        fun legal() = t.argentumSnapshot?.legalActions.orEmpty()
        fun type(i: org.json.JSONObject) = i.optJSONObject("action")?.optString("type").orEmpty()
        fun desc(i: org.json.JSONObject) = i.optString("description")
        fun pick(match: (org.json.JSONObject) -> Boolean): Boolean {
            val info = legal().firstOrNull(match) ?: return false
            val snap = t.argentumSnapshot ?: return false
            if (info.optBoolean("requiresTargets")) {
                // Target your own creature, as the lesson says (Giant Growth on Grizzly Bears).
                val you = snap.players[snap.viewingPlayer]
                val creature = you.battlefield.firstOrNull { "creature" in it.type.lowercase() } ?: return false
                val serverId = snap.serverCardIds[creature.id] ?: return false
                val action = org.json.JSONObject(info.getJSONObject("action").toString())
                    .put("targets", org.json.JSONArray().put(org.json.JSONObject().put("type", "Permanent").put("entityId", serverId)))
                return t.submitArgentumAction(action, com.arcanatable.ArgentumActions.epochFor(info, snap.interactionEpoch) ?: return false)
            }
            t.chooseArgentumLegalAction(info); return true
        }
        fun passUntilMoved(): Boolean {
            repeat(8) {
                runCatching { UiTestSupport.awaitReal(rule, 15_000, "a pass to be offered") { moved() || (!t.argentumAwaitingUpdate && legal().any { type(it) == "PassPriority" }) } }
                    .onFailure { return moved() }
                if (moved()) return true
                t.sendArgentumPriority()
                runCatching { UiTestSupport.awaitReal(rule, 15_000, "pass confirmed") { !t.argentumAwaitingUpdate } }
                UiTestSupport.settle(rule, 300)
                if (moved()) return true
            }
            return moved()
        }
        return when (event) {
            "LandPlayed" -> pick { type(it) == "PlayLand" }
            "ManaAdded" -> pick { type(it).contains("Activate") && ("Add" in desc(it) || "mana" in desc(it).lowercase()) }
            "SpellCast" -> pick { type(it) == "CastSpell" && ("Elvish Mystic" in desc(it) || "Giant Growth" in desc(it)) }
            "AttackersDeclared" -> {
                // Pass into combat, then declare Grizzly Bears on the real attack board.
                repeat(4) {
                    if (moved()) return true
                    if (legal().any { type(it) == "DeclareAttackers" }) {
                        t.openArgentumCombat(); UiTestSupport.settle(rule, 500)
                        val plan = t.argentumAttackPlan ?: return false
                        t.submitArgentumAction(plan.buildAction(plan.options.associate { it.attackerId to it.targets.first() }),
                            com.arcanatable.ArgentumActions.epochFor(plan.legalAction, t.argentumSnapshot?.interactionEpoch) ?: return false)
                        t.argentumAttackPlan = null
                        runCatching { UiTestSupport.awaitReal(rule, 15_000, "attack confirmed") { moved() } }
                        return moved()
                    }
                    if (!passUntilMoved() && legal().none { type(it) == "DeclareAttackers" }) return moved()
                }
                moved()
            }
            "SpellResolved", "PriorityPassed", "TurnAdvanced" -> passUntilMoved()
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
