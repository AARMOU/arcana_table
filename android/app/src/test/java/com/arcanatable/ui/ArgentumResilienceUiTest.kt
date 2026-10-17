package com.arcanatable.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasTestTag
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.arcanatable.ArgentumEndpoints
import com.arcanatable.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Offline UI tests of the real app against [FakeArgentumServer] (recorded Argentum payloads).
 * Covers: startup connection failure, needs-handling decision fallback, malformed data,
 * pause-not-reconnect while backgrounded, explicit resume, foreground auto-reconnect,
 * and the searchable diagnostic log.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi")
class ArgentumResilienceUiTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val fake = FakeArgentumServer()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        fake.start()
        seedDeck()
    }

    @After fun tearDown() {
        println("[diag-tail]\n" + diagnostics().lineSequence().toList().takeLast(25).joinToString("\n"))
        fake.shutdown()
    }

    private fun seedDeck() {
        var id = 2000
        fun card(name: String) = JSONObject().put("id", id++).put("name", name).put("type", "CARD").put("colorArgb", 0xFF547D9A.toInt())
            .put("tapped", false).put("token", false).put("faceDown", false).put("keywords", JSONArray()).put("counters", JSONObject())
        val library = JSONArray().apply { repeat(99) { put(card(if (it % 3 == 0) "Mountain" else "Forest")) } }
        fun seat(name: String, sid: String, computer: Boolean, lib: JSONArray, cmd: JSONObject?) = JSONObject()
            .put("name", name).put("id", sid).put("commander", cmd?.getString("name") ?: JSONObject.NULL).put("commanderCard", cmd ?: JSONObject.NULL)
            .put("computer", computer).put("deckLoaded", lib.length() > 0).put("life", 40)
            .put("library", lib).put("hand", JSONArray()).put("battlefield", JSONArray()).put("graveyard", JSONArray()).put("exile", JSONArray()).put("revealed", JSONArray())
        context.getSharedPreferences("arcana-table", Context.MODE_PRIVATE).edit()
            .putString("players", JSONArray().put(seat("TESTER", "s0", false, library, card("Bello, Bard of the Brambles"))).put(seat("AI", "s1", true, JSONArray(), null)).toString()).commit()
        context.getSharedPreferences("arcana_tutorial", Context.MODE_PRIVATE).edit()
            .putStringSet("first_visit_guides_v2", setOf("HOME", "TABLE", "PLAYER", "HAND", "MENU_MAIN", "MENU_TOOLS", "MENU_GAME", "MENU_DECKS", "LOBBY", "OPTIONS", "DECK_IMPORT", "STACK", "LOG")).commit()
    }

    private fun useServer(origin: String) {
        context.getSharedPreferences("argentum_session", Context.MODE_PRIVATE).edit().putString(ArgentumEndpoints.PREF_ORIGIN, origin).commit()
    }

    private fun shown(text: String, substring: Boolean = true) =
        rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun shot(scenario: ActivityScenario<MainActivity>, name: String) = scenario.onActivity { UiTestSupport.screenshot(it, "resilience-$name") }

    private fun diagnostics(): String = context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE).getString("diagnostic_events", "").orEmpty()

    @Test fun unreachableServerShowsRecoveryMessageNotEndlessLoading() {
        useServer("http://127.0.0.1:9") // nothing listens on the discard port
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        UiTestSupport.settle(rule)
        rule.onNodeWithText("PLAY WITH ARGENTUM").performClick()
        UiTestSupport.awaitReal(rule, 40_000, "deck-check error dialog") { shown("Argentum deck check") }
        shot(scenario, "01-unreachable")
        assertFalse("must not be stuck in a loading state", shown("CHECKING DECK WITH ARGENTUM"))
        rule.onNodeWithText("OK").performClick()
        UiTestSupport.settle(rule, 500)
        assertTrue("player can try again", shown("PLAY WITH ARGENTUM"))
        assertTrue(diagnostics().contains("ERROR"))
        assertFalse("server address must not be logged", diagnostics().contains("127.0.0.1"))
        scenario.close()
    }

    @Test fun unsupportedDecisionMalformedDataBackgroundPauseAndResume() {
        // An Argentum decision type Arcana has no control for (shape from PendingDecision.kt).
        val split = JSONObject().put("type", "SplitPilesDecision").put("id", "split-1").put("playerId", fake.viewingPlayerId)
            .put("prompt", "Separate these cards into two piles").put("cards", JSONArray(listOf("a", "b"))).put("numberOfPiles", 2)
        fake.stateMessage = fake.withDecision(split)
        useServer(fake.origin)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        UiTestSupport.settle(rule)

        // Start: real app flow against the fake server.
        rule.onNodeWithText("PLAY WITH ARGENTUM").performClick()
        UiTestSupport.awaitReal(rule, 30_000, "needs-handling decision panel") { shown("NEEDS HANDLING") && shown("REFRESH") }
        shot(scenario, "02-needs-handling-dialog")
        assertTrue(shown("splitting cards into piles"))

        // The player is never trapped: refresh asks Argentum again, view-table hides the panel.
        val before = fake.received.count { it.optString("type") == "requestResync" }
        rule.onNodeWithText("REFRESH").performClick()
        UiTestSupport.awaitReal(rule, 10_000, "resync request reaches server") { fake.received.count { it.optString("type") == "requestResync" } > before }
        rule.onNodeWithText("VIEW TABLE").performClick()
        UiTestSupport.awaitReal(rule, 5_000, "table with needs-handling strip") { !shown("REFRESH") && shown("Game active") }
        assertTrue("status strip keeps the notice visible", shown("NEEDS HANDLING"))
        shot(scenario, "03-needs-handling-strip")
        rule.onNode(hasTestTag("needs-handling")).performClick()
        UiTestSupport.awaitReal(rule, 5_000, "panel reopened from strip") { shown("REFRESH") }
        rule.onNodeWithText("VIEW TABLE").performClick()
        UiTestSupport.settle(rule, 300)

        // Malformed and unexpected data must not crash the app.
        fake.sendRaw("this is not json {")
        fake.send(JSONObject().put("type", "stateUpdate").put("state", "garbage"))
        fake.send(JSONObject().put("type", "someFutureMessageType").put("x", 1))
        UiTestSupport.awaitReal(rule, 10_000, "unreadable message logged as an error") { diagnostics().contains("could not read") }
        assertTrue("app still shows the game", shown("Game active"))

        // Backgrounded drop: Arcana pauses instead of reconnecting (Argentum would auto-resume).
        val connectionsBefore = fake.connections.get()
        scenario.moveToState(Lifecycle.State.STARTED) // onPause -> app in background
        fake.dropConnection()
        // Compose test APIs only see foreground windows, so confirm the pause via the app's own log here.
        UiTestSupport.awaitReal(rule, 15_000, "paused status recorded") { diagnostics().contains("connection status: paused") }
        Thread.sleep(4_000); UiTestSupport.settle(rule, 500)
        assertEquals("no automatic reconnect while backgrounded", connectionsBefore, fake.connections.get())
        scenario.moveToState(Lifecycle.State.RESUMED)
        UiTestSupport.settle(rule, 1_000)
        Thread.sleep(2_000); UiTestSupport.settle(rule, 500)
        assertEquals("returning to the app does not reconnect by itself", connectionsBefore, fake.connections.get())
        assertTrue("strip explains the pause", shown("PAUSED"))
        assertTrue(shown("RESUME", substring = false))
        shot(scenario, "04-paused-after-background")

        // Player chooses Resume -> reconnect with token -> Argentum pushes the game back.
        rule.onAllNodesWithText("RESUME", substring = false)[0].performSemanticsAction(SemanticsActions.OnClick)
        UiTestSupport.awaitReal(rule, 15_000, "resumed connection") { fake.connections.get() > connectionsBefore && shown("ENGINE CONNECTED") }
        assertTrue("reconnect presented the saved session token", fake.received.last { it.optString("type") == "connect" }.has("token"))
        shot(scenario, "05-resumed")

        // Foreground drop: reliability path reconnects automatically.
        val afterResume = fake.connections.get()
        fake.dropConnection()
        UiTestSupport.awaitReal(rule, 20_000, "automatic reconnect in foreground") { fake.connections.get() > afterResume && shown("ENGINE CONNECTED") }

        // Diagnostic log: searchable through the UI, categories recorded.
        rule.onNodeWithContentDescription("App menu").performClick()
        UiTestSupport.settle(rule, 300)
        rule.onNodeWithText("Game tools").performClick()
        UiTestSupport.settle(rule, 300)
        rule.onNodeWithText("Diagnostic log (search)").performClick()
        UiTestSupport.settle(rule, 500)
        rule.onNode(hasTestTag("diagnostic-search")).performTextInput("needs handling")
        UiTestSupport.settle(rule, 500)
        assertTrue(shown("SplitPilesDecision"))
        shot(scenario, "06-diagnostic-search")
        val log = diagnostics()
        listOf("CONNECTION", "ENGINE", "UNSUPPORTED", "ERROR").forEach { assertTrue("log has $it entries", log.contains("\t$it\t")) }
        assertFalse("server address must not be logged", log.contains("127.0.0.1") || log.contains(fake.origin))
        scenario.close()
    }

    @Test fun declareAttackersIsTheOnlyOfferAndArcanaSendsArgentumsShape() {
        // CombatEnumerator offers only DeclareAttackers in that step: no PassPriority. Before this
        // change Arcana's pass buttons had nothing to send and the game stalled.
        val me = fake.viewingPlayerId
        val opponent = fake.stateMessage.getJSONObject("state").getJSONArray("players").let { players ->
            (0 until players.length()).map { players.getJSONObject(it).optString("playerId") }.first { it != me }
        }
        val attack = JSONObject().put("actionType", "DeclareAttackers").put("description", "Declare attackers")
            .put("action", JSONObject().put("type", "DeclareAttackers").put("playerId", me).put("attackers", JSONObject()))
            .put("validAttackers", JSONArray(listOf("bear-1", "elf-2"))).put("validAttackTargets", JSONArray(listOf(opponent)))
        fake.stateMessage = JSONObject(fake.stateMessage.toString()).put("legalActions", JSONArray().put(attack)).put("pendingDecision", JSONObject.NULL)
        useServer(fake.origin)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        UiTestSupport.settle(rule)
        rule.onNodeWithText("PLAY WITH ARGENTUM").performClick()
        UiTestSupport.awaitReal(rule, 30_000, "primary button offers the declaration") { shown("DECLARE ATTACKERS") }
        rule.onAllNodesWithText("DECLARE ATTACKERS")[0].performSemanticsAction(SemanticsActions.OnClick)
        UiTestSupport.awaitReal(rule, 5_000, "attack dialog") { shown("Declare attackers") && shown("NO ATTACK") }
        rule.onAllNodesWithText("Object", substring = true)[0].performSemanticsAction(SemanticsActions.OnClick)
        UiTestSupport.awaitReal(rule, 5_000, "one attacker selected") { shown("ATTACK WITH 1") }
        scenario.onActivity { UiTestSupport.screenshot(it, "resilience-07-attack-dialog") }
        rule.onNodeWithText("ATTACK WITH 1").performClick()
        UiTestSupport.awaitReal(rule, 10_000, "submitAction reaches the server") { fake.received.any { it.optString("type") == "submitAction" } }
        val sent = fake.received.last { it.optString("type") == "submitAction" }
        val action = sent.getJSONObject("action")
        assertEquals("DeclareAttackers", action.getString("type"))
        assertEquals(me, action.getString("playerId"))
        assertEquals(1, action.getJSONObject("attackers").length())
        assertEquals(opponent, action.getJSONObject("attackers").getString(action.getJSONObject("attackers").keys().next()))
        assertTrue("interaction epoch echoed", sent.optString("interactionEpoch").isNotBlank())
        scenario.close()
    }

    @Test fun combatDamageBoardReplacesNeedsHandlingAndSubmitsArgentumShape() {
        val me = fake.viewingPlayerId
        val opponent = fake.stateMessage.getJSONObject("state").getJSONArray("players").let { players ->
            (0 until players.length()).map { players.getJSONObject(it).optString("playerId") }.first { it != me }
        }
        fun edge(id: String, src: String, tgt: String, dir: String, amount: Int, lethal: Int, drain: Boolean) = JSONObject()
            .put("id", id).put("sourceId", src).put("targetId", tgt).put("direction", dir).put("amount", amount)
            .put("maximum", 6).put("lethal", lethal).put("orderConstrained", true).put("isTrampleDrain", drain).put("editableBy", me)
        val board = JSONObject().put("type", "CombatResolutionDecision").put("id", "combat-1").put("playerId", me)
            .put("prompt", "Assign Wildsear, Scouring Maw's 6 combat damage").put("firstStrike", false)
            .put("context", JSONObject())
            .put("attackers", JSONArray().put(JSONObject().put("id", "wild").put("name", "Wildsear, Scouring Maw").put("power", 6).put("hasTrample", true)))
            .put("blockers", JSONArray().put(JSONObject().put("id", "wall").put("name", "Stone Wall").put("toughness", 3)))
            .put("defenders", JSONArray().put(JSONObject().put("id", opponent).put("name", "Opponent").put("kind", "PLAYER")))
            .put("edges", JSONArray().put(edge("wild->wall", "wild", "wall", "ATTACKER_TO_BLOCKER", 3, 3, false))
                .put(edge("wild->$opponent", "wild", opponent, "ATTACKER_TO_PLAYER", 3, 1, true)))
        fake.stateMessage = fake.withDecision(board)
        useServer(fake.origin)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        UiTestSupport.settle(rule)
        rule.onNodeWithText("PLAY WITH ARGENTUM").performClick()
        UiTestSupport.awaitReal(rule, 30_000, "combat damage board") { shown("CONFIRM DAMAGE") }
        assertFalse("combat damage is no longer a needs-handling case", shown("NEEDS HANDLING"))
        assertTrue(shown("Stone Wall") && shown("lethal 3"))
        scenario.onActivity { UiTestSupport.screenshot(it, "resilience-08-combat-damage") }
        rule.onNodeWithText("CONFIRM DAMAGE").performClick()
        UiTestSupport.awaitReal(rule, 10_000, "decision answer reaches server") {
            fake.received.any { it.optString("type") == "submitAction" && it.optJSONObject("action")?.optString("type") == "SubmitDecision" }
        }
        val response = fake.received.last { it.optString("type") == "submitAction" }.getJSONObject("action").getJSONObject("response")
        assertEquals("CombatResolutionResponse", response.getString("type"))
        assertEquals("combat-1", response.getString("decisionId"))
        assertEquals(2, response.getJSONArray("edges").length())
        assertEquals(3, response.getJSONArray("edges").getJSONObject(0).getInt("amount"))
        scenario.close()
    }
}
