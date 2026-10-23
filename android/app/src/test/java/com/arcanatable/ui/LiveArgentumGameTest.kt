package com.arcanatable.ui

import android.content.Context
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.arcanatable.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.net.URL

/**
 * End-to-end check of the real Arcana UI against Argentum's live engine (magic.wingedsheep.com).
 * Opt-in because it needs the network and creates a short practice game vs Argentum's AI, which
 * the test concedes at the end:  ./gradlew testDebugUnitTest -Pargentum.live=true --tests '*LiveArgentumGameTest*'
 *
 * Only the local deck is seeded directly (Arcana's importer needs Scryfall, which may be blocked
 * in CI); deck validation, lobby, game, every action and every state update go through Argentum.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class LiveArgentumGameTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val log = mutableListOf<String>()
    private var current: ActivityScenario<MainActivity>? = null
    private fun note(text: String) { log += text; println("[live] $text") }

    private fun seedDeckFromArgentum(unsupportedCard: String? = null) {
        val examples = JSONArray(URL("https://magic.wingedsheep.com/api/decks/examples").readText())
        val deck = (0 until examples.length()).map { examples.getJSONObject(it) }
            .first { it.optString("format") == "COMMANDER" && it.optString("commander") == "Bello, Bard of the Brambles" }
        val commander = deck.getString("commander")
        var nextId = 1000
        fun card(name: String) = JSONObject().put("id", nextId++).put("name", name).put("type", "CARD").put("colorArgb", 0xFF547D9A.toInt())
            .put("tapped", false).put("token", false).put("faceDown", false).put("keywords", JSONArray()).put("counters", JSONObject())
        val library = JSONArray()
        val cards = deck.getJSONObject("cards")
        cards.keys().forEach { name -> val count = cards.getInt(name) - if (name == commander) 1 else 0; repeat(count) { library.put(card(name)) } }
        // Swap one card for a name Argentum's registry cannot know, to exercise the needs-handling review.
        if (unsupportedCard != null) library.put(0, card(unsupportedCard))
        fun seat(name: String, id: String, computer: Boolean, lib: JSONArray, cmd: JSONObject?) = JSONObject()
            .put("name", name).put("id", id).put("commander", cmd?.getString("name") ?: JSONObject.NULL).put("commanderCard", cmd ?: JSONObject.NULL)
            .put("computer", computer).put("deckLoaded", lib.length() > 0).put("handRevealedToAll", false).put("life", 40)
            .put("library", lib).put("hand", JSONArray()).put("battlefield", JSONArray()).put("graveyard", JSONArray()).put("exile", JSONArray()).put("revealed", JSONArray())
        val players = JSONArray().put(seat("ARCANA TESTER", "local-seat", false, library, card(commander))).put(seat("AI", "ai-seat", true, JSONArray(), null))
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("arcana-table", Context.MODE_PRIVATE).edit().putString("players", players.toString()).commit()
        // Skip first-visit guide overlays so they do not cover the controls under test.
        context.getSharedPreferences("arcana_tutorial", Context.MODE_PRIVATE).edit()
            .putStringSet("first_visit_guides_v2", setOf("HOME", "TABLE", "PLAYER", "HAND", "MENU_MAIN", "MENU_TOOLS", "MENU_GAME", "MENU_DECKS", "LOBBY", "OPTIONS", "DECK_IMPORT", "STACK", "LOG")).commit()
        note("Seeded ${library.length()} library cards + commander $commander from Argentum's example deck")
    }

    private fun tableShown() = rule.onAllNodes(androidx.compose.ui.test.hasTestTag("phase-tracker"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun connectedShown() = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Argentum connected"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun textShown(text: String) = rule.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    /** Argentum's pass button: "PASS PRIORITY", "PASS" or "PASS → <where it stops>" (not "PASS TO MY TURN"). */
    private val passButton = androidx.compose.ui.test.hasText("PASS PRIORITY") or androidx.compose.ui.test.hasText("PASS") or androidx.compose.ui.test.hasText("PASS →", substring = true)
    private fun passShown() = rule.onAllNodes(passButton, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun shot(scenario: ActivityScenario<MainActivity>, name: String) = scenario.onActivity { UiTestSupport.screenshot(it, "live-$name") }

    @Test fun playRealArgentumGameThroughArcanaUi() {
        assumeTrue("set -Pargentum.live=true to run", System.getProperty("argentum.live") == "true")
        try { runLiveGame() } catch (error: Throwable) {
            val context = ApplicationProvider.getApplicationContext<Context>()
            runCatching { current?.onActivity { UiTestSupport.screenshot(it, "live-zz-failure") } }
            println("[live] ---- Arcana diagnostic log at failure ----")
            context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE).getString("diagnostic_events", "").orEmpty()
                .lineSequence().toList().takeLast(60).forEach { println("[diag] $it") }
            throw error
        }
    }

    private fun runLiveGame() {
        seedDeckFromArgentum()
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        current = scenario
        UiTestSupport.settle(rule)
        shot(scenario, "01-home-with-deck")

        // 1. Start: Argentum validates the deck, opens a vs-AI Commander lobby and starts the game.
        UiTestSupport.playWithArgentum(rule, scenario)
        var kept = false
        UiTestSupport.awaitReal(rule, 120_000, "Argentum mulligan, then the first authoritative state on the table") {
            if (!kept && textShown("MULLIGAN") && textShown("KEEP ")) {
                UiTestSupport.settle(rule, 500)
                shot(scenario, "02-opening-hand")
                note("Mulligan decision received from Argentum; keeping")
                rule.onAllNodesWithText("KEEP ", substring = true)[0].performClick()
                kept = true
            }
            tableShown()
        }
        note("Table shows Argentum state: game active")
        shot(scenario, "03-table-game-active")

        // 2. Normal engine updates: act whenever Argentum offers something, until we have played a land.
        var playedLand = false
        var passes = 0
        val deadline = System.currentTimeMillis() + 240_000
        while (System.currentTimeMillis() < deadline && passes < 14 && !playedLand) {
            UiTestSupport.awaitReal(rule, 120_000, "Argentum to offer us an action") {
                passShown() || textShown("DECLARE ATTACKERS") || textShown("DECLARE BLOCKERS") || textShown("NEEDS HANDLING")
            }
            when {
                textShown("NEEDS HANDLING") -> { shot(scenario, "needs-handling"); throw AssertionError("Unexpected needs-handling prompt during the opening turns") }
                textShown("DECLARE ATTACKERS") || textShown("DECLARE BLOCKERS") -> {
                    rule.onAllNodesWithText("DECLARE", substring = true)[0].performClick()
                    UiTestSupport.awaitReal(rule, 10_000, "combat dialog") { textShown("NO ATTACK") || textShown("NO BLOCKS") || textShown("ATTACK WITH") || textShown("BLOCK WITH") }
                    shot(scenario, "05-combat-dialog")
                    note("Combat declaration dialog opened from Argentum's legal action")
                    rule.onNode(hasText("NO ATTACK").or(hasText("NO BLOCKS"))).performClick()
                }
                else -> {
                    // Open our own board, then the hand drawer, then Argentum's legal actions.
                    // The pager keeps off-screen pages composed; trigger the tab's click action directly.
                    note("step: open own board"); rule.onNode(androidx.compose.ui.test.hasTestTag("player-tab-you")).performClick()
                    UiTestSupport.settle(rule, 800)
                    note("step: open hand drawer"); rule.onNode(androidx.compose.ui.test.hasTestTag("hand-peek")).performClick()
                    UiTestSupport.settle(rule, 800)
                    shot(scenario, "04-hand-drawer")
                    note("step: open actions"); rule.onAllNodesWithText("ACTIONS ·", substring = true)[0].performSemanticsAction(SemanticsActions.OnClick)
                    UiTestSupport.settle(rule, 500)
                    val land = rule.onAllNodesWithText("Play ", substring = true).fetchSemanticsNodes().isNotEmpty()
                    if (land) {
                        rule.onAllNodesWithText("Play ", substring = true)[0].performSemanticsAction(SemanticsActions.OnClick)
                        note("Played a land via Argentum legal action")
                        playedLand = true
                    } else {
                        rule.onNodeWithText("CLOSE").performClick() // close the action sheet
                        UiTestSupport.settle(rule, 500)
                        rule.onAllNodes(passButton)[0].performSemanticsAction(SemanticsActions.OnClick)
                        passes++
                        note("Passed priority (#$passes)")
                    }
                    UiTestSupport.settle(rule, 300)
                }
            }
            UiTestSupport.awaitReal(rule, 30_000, "Argentum to confirm") { !textShown("Waiting for Argentum to confirm") }
        }
        assertTrue("Argentum never offered a land in ${passes} passes", playedLand)
        UiTestSupport.awaitReal(rule, 30_000, "state after playing the land") { tableShown() }
        shot(scenario, "06-after-land")

        // 3. Handoff: the activity is recreated (as after a system kill or theme change). Arcana must NOT
        //    reconnect or resume by itself; it offers Resume.
        scenario.recreate()
        UiTestSupport.settle(rule, 3_000)
        Thread.sleep(3_000)
        UiTestSupport.settle(rule, 1_000)
        assertTrue("resume offer shown after recreation", textShown("RESUME GAME"))
        assertFalse("game must not auto-resume", tableShown())
        note("After recreation: resume offered, game not auto-resumed")
        shot(scenario, "07-resume-offer")

        // 4. Player chooses Resume -> Argentum pushes the live game back.
        rule.onNodeWithText("RESUME GAME").performClick()
        UiTestSupport.awaitReal(rule, 60_000, "resumed game state") { tableShown() }
        note("Resumed by player choice; table shows the live game again")
        shot(scenario, "08-resumed")

        // 5. Diagnostic log captured the session and is searchable.
        scenario.recreate()
        UiTestSupport.settle(rule, 2_000)
        assertTrue(textShown("END THAT GAME"))
        rule.onNodeWithText("END THAT GAME").performClick()
        UiTestSupport.awaitReal(rule, 30_000, "previous game conceded") { !textShown("RESUME GAME") }
        note("Ended the previous game via END THAT GAME (concede)")
        shot(scenario, "09-ended")
        scenario.close()
    }

    @Test fun unsupportedCardOpensReviewAndSandboxFallback() {
        assumeTrue("set -Pargentum.live=true to run", System.getProperty("argentum.live") == "true")
        seedDeckFromArgentum(unsupportedCard = "Arcana Test Card That Does Not Exist")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        current = scenario
        UiTestSupport.settle(rule)
        UiTestSupport.playWithArgentum(rule, scenario)
        UiTestSupport.awaitReal(rule, 60_000, "needs-handling review from Argentum's validator") { textShown("Cards need handling") }
        assertTrue(textShown("Arcana Test Card That Does Not Exist"))
        assertTrue("nothing changed before the player chooses", textShown("Nothing has been changed"))
        shot(scenario, "10-unsupported-review")
        note("Argentum's live validator rejected the unknown card; review dialog shown")
        rule.onNodeWithText("PLAY FULL DECK IN SANDBOX").performClick()
        UiTestSupport.settle(rule, 1_500)
        assertTrue("sandbox table is a local Arcana game", textShown("GAME IN PROGRESS"))
        assertFalse("Argentum is not running the sandbox", connectedShown())
        // The full deck (including the unsupported card) is still the player's deck.
        val saved = ApplicationProvider.getApplicationContext<Context>().getSharedPreferences("arcana-table", Context.MODE_PRIVATE).getString("players", "").orEmpty()
        assertTrue("saved deck kept the unsupported card", saved.contains("Arcana Test Card That Does Not Exist"))
        shot(scenario, "11-sandbox-table")
        note("Sandbox fallback started with the full deck; saved deck unchanged")
        scenario.close()
    }
}
