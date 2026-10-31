package com.arcanatable.ui

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
 * pause-not-reconnect while backgrounded, automatic return-from-Control resume, foreground auto-reconnect,
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

    /** The compact Argentum table is up (its phase strip is drawn). */

    private fun tableShown() = rule.onAllNodes(hasTestTag("phase-tracker"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    /** The connection dot reports a live Argentum connection. */

    private fun connectedShown() = rule.onAllNodes(androidx.compose.ui.test.hasContentDescription("Argentum connected"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()


    private fun shown(text: String, substring: Boolean = true) =
        rule.onAllNodesWithText(text, substring = substring, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun shot(scenario: ActivityScenario<MainActivity>, name: String) = scenario.onActivity { UiTestSupport.screenshot(it, "resilience-$name") }

    private fun diagnostics(): String = context.getSharedPreferences("arcana_options", Context.MODE_PRIVATE).getString("diagnostic_events", "").orEmpty()

    @Test fun unreachableServerShowsRecoveryMessageNotEndlessLoading() {
        useServer("http://127.0.0.1:9") // nothing listens on the discard port
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        UiTestSupport.settle(rule)
        UiTestSupport.playWithArgentum(rule, scenario)
        UiTestSupport.awaitReal(rule, 40_000, "start-up error dialog") { shown("Couldn't start the game") }
        shot(scenario, "01-unreachable")
        assertFalse("must not be stuck in a loading state", shown("CHECKING DECK WITH ARGENTUM"))
        rule.onNodeWithText("OK").performClick()
        UiTestSupport.settle(rule, 500)
        assertTrue("player can try again", UiTestSupport.homeShown(rule))
        assertTrue(diagnostics().contains("ERROR"))
        assertFalse("server address must not be logged", diagnostics().contains("127.0.0.1"))
        scenario.close()
    }

    @Test fun unsupportedDecisionMalformedDataBackgroundPauseAndResume() {
        // A decision type Arcana has no control for (a future Argentum addition).
        val split = JSONObject().put("type", "TimeTravelDecision").put("id", "future-1").put("playerId", fake.viewingPlayerId)
            .put("prompt", "Choose a moment in time")
        fake.stateMessage = fake.withDecision(split)
        useServer(fake.origin)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        UiTestSupport.settle(rule)

        // Start: real app flow against the fake server.
        UiTestSupport.playWithArgentum(rule, scenario)
        UiTestSupport.awaitReal(rule, 30_000, "needs-handling decision panel") { shown("NEEDS HANDLING") && shown("REFRESH") }
        shot(scenario, "02-needs-handling-dialog")
        assertTrue(shown("time travel"))

        // The player is never trapped: refresh asks Argentum again, view-table hides the panel.
        val before = fake.received.count { it.optString("type") == "requestResync" }
        rule.onNodeWithText("REFRESH").performClick()
        UiTestSupport.awaitReal(rule, 10_000, "resync request reaches server") { fake.received.count { it.optString("type") == "requestResync" } > before }
        rule.onNodeWithText("VIEW TABLE").performClick()
        UiTestSupport.awaitReal(rule, 5_000, "table with needs-handling strip") { !shown("REFRESH") && tableShown() }
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
        assertTrue("app still shows the game", tableShown())
        // Malformed data deliberately opens a recovery dialog. Acknowledge it before testing
        // lifecycle and menu actions, otherwise those gestures hit the modal instead of the board.
        UiTestSupport.awaitReal(rule, 5_000, "malformed-message recovery dialog") {
            shown("Argentum sent a message Arcana could not read.")
        }
        rule.onNodeWithText("OK").performClick()
        UiTestSupport.settle(rule, 300)
        assertFalse("recovery dialog dismissed", shown("Argentum sent a message Arcana could not read."))

        // Backgrounded drop: Arcana pauses instead of reconnecting (Argentum would auto-resume).
        val connectionsBefore = fake.connections.get()
        scenario.moveToState(Lifecycle.State.STARTED) // onPause -> app in background
        fake.dropConnection()
        // Compose test APIs only see foreground windows, so confirm the pause via the app's own log here.
        UiTestSupport.awaitReal(rule, 15_000, "paused status recorded") { diagnostics().contains("connection status: paused") }
        Thread.sleep(4_000); UiTestSupport.settle(rule, 500)
        assertEquals("no automatic reconnect while backgrounded", connectionsBefore, fake.connections.get())
        // Returning from Control must recover the PAUSED session without a tap.
        scenario.moveToState(Lifecycle.State.RESUMED)
        UiTestSupport.awaitReal(rule, 15_000, "automatically reconnected after returning to Arcana") {
            fake.connections.get() > connectionsBefore && connectedShown()
        }
        assertTrue("automatic reconnect must reuse the saved seat/session token",
            fake.received.last { it.optString("type") == "connect" }.has("token"))
        assertTrue("foreground recovery should be logged",
            diagnostics().contains("Returning to Arcana: reconnecting paused Argentum session"))
        shot(scenario, "04-auto-resumed-after-background")

        // Foreground drop: reliability path reconnects automatically.
        val afterResume = fake.connections.get()
        fake.dropConnection()
        UiTestSupport.awaitReal(rule, 20_000, "automatic reconnect in foreground") { fake.connections.get() > afterResume && connectedShown() }

        // Diagnostic log: searchable through the UI, categories recorded.
        rule.onNodeWithContentDescription("App menu").performClick()
        UiTestSupport.settle(rule, 300)
        rule.onNodeWithText("Options").performClick()
        UiTestSupport.settle(rule, 500)
        // Options → Help & diagnostics (the page scrolls; bring the row into view first).
        // performScrollTo repeatedly checks bounds; with the manual game clock paused its scroll
        // coroutine never gets a frame and that loop cannot finish. Let Compose drive this action.
        rule.mainClock.autoAdvance = true
        try {
            rule.onNodeWithText("Diagnostic log").performScrollTo()
        } finally {
            rule.mainClock.autoAdvance = false
        }
        rule.onNodeWithText("Diagnostic log").performClick()
        UiTestSupport.settle(rule, 500)
        rule.onNode(hasTestTag("diagnostic-search")).performTextInput("needs handling")
        UiTestSupport.settle(rule, 500)
        assertTrue(shown("TimeTravelDecision"))
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
        UiTestSupport.playWithArgentum(rule, scenario)
        UiTestSupport.awaitReal(rule, 30_000, "primary button offers the declaration") { shown("DECLARE ATTACKERS") }
        rule.onAllNodesWithText("DECLARE ATTACKERS")[0].performSemanticsAction(SemanticsActions.OnClick)
        UiTestSupport.awaitReal(rule, 5_000, "attack board") { rule.onAllNodes(hasTestTag("combat-board"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() && shown("NO ATTACK") }
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
        UiTestSupport.playWithArgentum(rule, scenario)
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

    @Test fun manaAbilitiesAreHiddenFromTheActionSheet() {
        // Live Build 89: "{T}: Add {G}" on a land that entered tapped was rejected three times with
        // INVALID_ACTION. Argentum pays mana itself, so the sheet and its count leave mana abilities out.
        val me = fake.viewingPlayerId
        val legal = fake.stateMessage.getJSONArray("legalActions")
        val playForest = (0 until legal.length()).map { legal.getJSONObject(it) }.first { it.optString("description") == "Play Forest" }
        val tapForGreen = JSONObject().put("actionType", "ActivateAbility").put("description", "{T}: Add {G}").put("isManaAbility", true)
            .put("action", JSONObject().put("type", "ActivateAbility").put("playerId", me).put("sourceId", playForest.getJSONObject("action").getString("cardId")).put("abilityId", "mana-g"))
        fake.stateMessage = JSONObject(fake.stateMessage.toString()).put("legalActions", JSONArray().put(tapForGreen).put(playForest)).put("pendingDecision", JSONObject.NULL)
        useServer(fake.origin)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        UiTestSupport.settle(rule)
        UiTestSupport.playWithArgentum(rule, scenario)
        UiTestSupport.awaitReal(rule, 30_000, "table") { tableShown() }
        rule.onNode(hasTestTag("player-tab-you")).performClick()
        UiTestSupport.settle(rule, 800)
        rule.onNode(hasTestTag("hand-peek")).performClick()
        UiTestSupport.settle(rule, 800)
        assertTrue("count leaves the mana ability out", shown("ACTIONS · 1"))
        rule.onAllNodesWithText("ACTIONS ·", substring = true)[0].performSemanticsAction(SemanticsActions.OnClick)
        UiTestSupport.awaitReal(rule, 5_000, "action sheet") { shown("Play Forest") }
        UiTestSupport.settle(rule, 1_200)
        scenario.onActivity { UiTestSupport.screenshot(it, "resilience-12-no-mana-abilities") }
        assertFalse("mana ability must not be offered", shown("Add {G}"))
        // Build 128 also supports explicitly floating mana from a source's own sheet, so the
        // global action list no longer claims that all mana use is automatic.
        assertFalse("global actions do not show the retired automatic-only mana hint", shown("Mana is paid automatically"))
        assertTrue("nothing was sent", fake.received.none { it.optString("type") == "submitAction" })
        scenario.close()
    }

    @Test fun composerSendsDividedDamageSpellThenGameOverReturnsHome() {
        val me = fake.viewingPlayerId
        val state = fake.stateMessage.getJSONObject("state")
        val opponent = state.getJSONArray("players").let { p -> (0 until p.length()).map { p.getJSONObject(it).optString("playerId") }.first { it != me } }
        val handCard = state.getJSONArray("zones").let { z -> (0 until z.length()).map { z.getJSONObject(it) }
            .first { it.getJSONObject("zoneId").optString("zoneType") == "Hand" && it.getJSONObject("zoneId").optString("ownerId") == me }
            .getJSONArray("cardIds").getString(0) }
        val oppPermanent = state.getJSONArray("zones").let { z -> (0 until z.length()).map { z.getJSONObject(it) }
            .first { it.getJSONObject("zoneId").optString("zoneType") == "Battlefield" }.getJSONArray("cardIds").getString(0) }
        val arc = JSONObject().put("actionType", "CastSpell").put("description", "Cast Arc Lightning").put("manaCostString", "{2}{R}")
            .put("action", JSONObject().put("type", "CastSpell").put("playerId", me).put("cardId", handCard))
            .put("requiresTargets", true).put("requiresDamageDistribution", true).put("totalDamageToDistribute", 3).put("minDamagePerTarget", 1)
            .put("targetRequirements", JSONArray().put(JSONObject().put("index", 0).put("description", "Any number of targets").put("minTargets", 1).put("maxTargets", 3)
                .put("validTargets", JSONArray(listOf(opponent, oppPermanent)))))
        fake.stateMessage = JSONObject(fake.stateMessage.toString()).put("legalActions", JSONArray().put(arc)).put("pendingDecision", JSONObject.NULL)
        useServer(fake.origin)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        UiTestSupport.settle(rule)
        UiTestSupport.playWithArgentum(rule, scenario)
        UiTestSupport.awaitReal(rule, 30_000, "table") { tableShown() }
        // Open Argentum's actions from the hand drawer and pick the spell.
        rule.onNode(hasTestTag("player-tab-you")).performClick()
        UiTestSupport.settle(rule, 800)
        rule.onNode(hasTestTag("hand-peek")).performClick()
        UiTestSupport.settle(rule, 800)
        rule.onAllNodesWithText("ACTIONS ·", substring = true)[0].performSemanticsAction(SemanticsActions.OnClick)
        UiTestSupport.awaitReal(rule, 5_000, "action sheet") { shown("Cast Arc Lightning") }
        UiTestSupport.settle(rule, 1_200) // let the sheet finish sliding in
        scenario.onActivity { UiTestSupport.screenshot(it, "resilience-09-action-sheet") }
        rule.onAllNodesWithText("Cast Arc Lightning")[0].performSemanticsAction(SemanticsActions.OnClick)
        UiTestSupport.awaitReal(rule, 5_000, "composer") { shown("Divide 3") }
        // Choose both targets; the composer splits 3 as 2 + 1 and enables CONFIRM.
        val opponentName = state.getJSONArray("players").let { p -> (0 until p.length()).map { p.getJSONObject(it) }.first { it.optString("playerId") == opponent }.getString("name") }
        val permanentName = state.getJSONObject("cards").getJSONObject(oppPermanent).getString("name")
        val clickableRow = { text: String -> androidx.compose.ui.test.hasText(text) and androidx.compose.ui.test.hasClickAction() }
        rule.onAllNodes(clickableRow(opponentName))[0].performSemanticsAction(SemanticsActions.OnClick)
        UiTestSupport.settle(rule, 300)
        rule.onAllNodes(clickableRow(permanentName))[0].performSemanticsAction(SemanticsActions.OnClick)
        UiTestSupport.settle(rule, 500)
        scenario.onActivity { UiTestSupport.screenshot(it, "resilience-10-composer") }
        rule.onNodeWithText("CONFIRM").performClick()
        UiTestSupport.awaitReal(rule, 10_000, "cast reaches server") { fake.received.any { it.optString("type") == "submitAction" } }
        val action = fake.received.last { it.optString("type") == "submitAction" }.getJSONObject("action")
        assertEquals("CastSpell", action.getString("type"))
        assertEquals(2, action.getJSONArray("targets").length())
        assertEquals(3, action.getJSONObject("damageDistribution").let { d -> d.keys().asSequence().sumOf { d.getInt(it) } })

        // Game over: result screen, then HOME restores the local table.
        fake.send(JSONObject().put("type", "gameOver").put("winnerId", me).put("reason", "LIFE_ZERO"))
        UiTestSupport.awaitReal(rule, 10_000, "game over screen") { shown("Victory") && shown("PLAY AGAIN") }
        assertFalse("legacy room end-of-game dialog must not appear in Argentum games", shown("unanimous vote"))
        scenario.onActivity { UiTestSupport.screenshot(it, "resilience-11-game-over") }
        rule.onNodeWithText("HOME").performClick()
        UiTestSupport.awaitReal(rule, 5_000, "home") { UiTestSupport.homeShown(rule) }
        assertFalse("no resume offer after a finished game", shown("RESUME GAME"))
        scenario.close()
    }
}
