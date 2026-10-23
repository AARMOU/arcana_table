package com.arcanatable.ui

import android.content.Context
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.arcanatable.ArgentumEndpoints
import com.arcanatable.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Mid-game screenshots for design review: both players have permanents (some tapped), one is
 * attacking, there is a game log and a little mana. Built from the recorded main-phase state.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DesignPreviewTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val fake = FakeArgentumServer()
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        fake.start()
        fake.stateMessage = midgame(FakeArgentumServer.fixture("state-main-phase-with-actions.json"))
        var id = 3000
        fun card(name: String) = JSONObject().put("id", id++).put("name", name).put("type", "CARD").put("colorArgb", 0xFF547D9A.toInt())
            .put("tapped", false).put("token", false).put("faceDown", false).put("keywords", JSONArray()).put("counters", JSONObject())
        val library = JSONArray().apply { repeat(99) { put(card("Forest")) } }
        fun seat(name: String, sid: String, computer: Boolean, lib: JSONArray, cmd: JSONObject?) = JSONObject()
            .put("name", name).put("id", sid).put("commander", cmd?.getString("name") ?: JSONObject.NULL).put("commanderCard", cmd ?: JSONObject.NULL)
            .put("computer", computer).put("deckLoaded", lib.length() > 0).put("life", 40)
            .put("library", lib).put("hand", JSONArray()).put("battlefield", JSONArray()).put("graveyard", JSONArray()).put("exile", JSONArray()).put("revealed", JSONArray())
        context.getSharedPreferences("arcana-table", Context.MODE_PRIVATE).edit()
            .putString("players", JSONArray().put(seat("TESTER", "s0", false, library, card("Bello, Bard of the Brambles"))).put(seat("AI", "s1", true, JSONArray(), null)).toString()).commit()
        context.getSharedPreferences("arcana_tutorial", Context.MODE_PRIVATE).edit()
            .putStringSet("first_visit_guides_v2", setOf("HOME", "TABLE", "PLAYER", "HAND", "MENU_MAIN", "MENU_TOOLS", "MENU_GAME", "MENU_DECKS", "LOBBY", "OPTIONS", "DECK_IMPORT", "STACK", "LOG")).commit()
        context.getSharedPreferences("argentum_session", Context.MODE_PRIVATE).edit().putString(ArgentumEndpoints.PREF_ORIGIN, fake.origin).commit()
    }

    @After fun tearDown() = fake.shutdown()

    private fun midgame(message: JSONObject): JSONObject {
        val state = message.getJSONObject("state")
        val me = state.getString("viewingPlayerId")
        val players = state.getJSONArray("players")
        val ai = (0 until players.length()).map { players.getJSONObject(it).getString("playerId") }.first { it != me }
        val cards = state.getJSONObject("cards")
        val zones = state.getJSONArray("zones")
        fun zone(type: String, owner: String): JSONObject = (0 until zones.length()).map { zones.getJSONObject(it) }
            .firstOrNull { it.getJSONObject("zoneId").getString("zoneType") == type && it.getJSONObject("zoneId").getString("ownerId") == owner }
            ?: JSONObject().put("zoneId", JSONObject().put("zoneType", type).put("ownerId", owner)).put("cardIds", JSONArray()).put("size", 0).also { zones.put(it) }
        val hand = zone("Hand", me)
        val handIds = hand.getJSONArray("cardIds")
        val moved = (0 until minOf(4, handIds.length())).map { handIds.getString(it) }
        val keep = JSONArray().apply { (moved.size until handIds.length()).forEach { put(handIds.getString(it)) } }
        hand.put("cardIds", keep).put("size", keep.length())
        (0 until players.length()).map { players.getJSONObject(it) }.first { it.getString("playerId") == me }.put("handSize", keep.length())
        val myField = zone("Battlefield", me)
        val aiField = zone("Battlefield", ai)
        var n = 0
        fun place(id: String, owner: String, field: JSONObject, tapped: Boolean, attacking: Boolean = false) {
            val card = JSONObject(cards.getJSONObject(id).toString())
            val newId = "dp-${n++}"
            card.put("id", newId).put("ownerId", owner).put("controllerId", owner).put("isTapped", tapped).put("isAttacking", attacking)
                .put("hasSummoningSickness", false).put("zone", JSONObject().put("zoneType", "Battlefield").put("ownerId", owner))
            cards.put(newId, card)
            field.getJSONArray("cardIds").put(newId)
            field.put("size", field.getJSONArray("cardIds").length())
        }
        moved.forEachIndexed { i, id -> place(id, me, myField, tapped = i == 1); place(id, me, myField, tapped = false) }
        moved.take(3).forEachIndexed { i, id -> place(id, ai, aiField, tapped = i == 0, attacking = i == 2) }
        val log = state.optJSONArray("gameLog") ?: JSONArray().also { state.put("gameLog", it) }
        log.put(JSONObject().put("type", "turnChanged").put("isYourTurn", false).put("description", "--- Turn 3 (Opponent's turn) ---"))
        log.put(JSONObject().put("type", "spellCast").put("controllerId", ai).put("description", "Opponent cast Darkstone Rat"))
        log.put(JSONObject().put("type", "lifeChanged").put("isYours", true).put("description", "You lost 3 life"))
        players.getJSONObject(players.length() - 1).put("life", 37)
        (0 until players.length()).map { players.getJSONObject(it) }.first { it.getString("playerId") == me }
            .put("manaPool", JSONObject().put("GREEN", 2).put("COLORLESS", 1))
        message.put("nextStopPoint", "To Combat")
        return message
    }

    private fun shown(text: String) = rule.onAllNodesWithText(text, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun run(name: String) {
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        UiTestSupport.settle(rule)
        UiTestSupport.playWithArgentum(rule)
        runCatching { UiTestSupport.awaitReal(rule, 30_000, "Argentum table") { rule.onAllNodes(hasTestTag("phase-tracker"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() || shown("Game active") } }
            .onFailure { scenario.onActivity { a -> UiTestSupport.screenshot(a, "design-$name-0-timeout") }; throw it }
        UiTestSupport.settle(rule, 1_000)
        rule.onNode(hasTestTag("player-tab-you")).performClick()
        UiTestSupport.settle(rule, 1_000)
        scenario.onActivity { UiTestSupport.screenshot(it, "design-$name-1-your-board") }
        assertTrue(shown("PASS → TO COMBAT"))
        // Tap-and-hold shows the card large; letting go without moving keeps it up until a tap.
        // A card shows its name as text until its art loads, then as the art's description (PC builds load art).
        rule.onAllNodes(androidx.compose.ui.test.hasText("Rain of Riches", substring = true) or androidx.compose.ui.test.hasContentDescription("Rain of Riches", substring = true))[0]
            .performTouchInput { longClick() }
        UiTestSupport.settle(rule, 800)
        assertTrue("hold-to-preview opened", rule.onAllNodes(hasTestTag("card-zoom"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
        scenario.onActivity { UiTestSupport.screenshot(it, "design-$name-1b-hold-preview") }
        rule.onNode(hasTestTag("card-zoom")).performClick()
        UiTestSupport.settle(rule, 600)
        assertTrue("tap closes the preview", rule.onAllNodes(hasTestTag("card-zoom"), useUnmergedTree = true).fetchSemanticsNodes().isEmpty())
        rule.onNode(hasTestTag("player-tab-0")).performClick()
        UiTestSupport.settle(rule, 1_000)
        scenario.onActivity { UiTestSupport.screenshot(it, "design-$name-2-opponent-board") }
        rule.onAllNodesWithText("TABLE", substring = false)[0].performClick()
        UiTestSupport.settle(rule, 1_000)
        scenario.onActivity { UiTestSupport.screenshot(it, "design-$name-3-table") }
        rule.onNode(hasTestTag("player-tab-you")).performClick()
        UiTestSupport.settle(rule, 1_500)
        runCatching { rule.onNode(hasTestTag("hand-peek")).performClick() }
            .onFailure { scenario.onActivity { a -> UiTestSupport.screenshot(a, "design-$name-x-no-hand-peek") }; throw it }
        UiTestSupport.settle(rule, 1_000)
        scenario.onActivity { UiTestSupport.screenshot(it, "design-$name-4-hand") }
        scenario.close()
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun phonePortrait() = run("phone-portrait")
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun phoneLandscape() = run("phone-landscape")
    @Test @Config(sdk = [34], qualifiers = "w344dp-h882dp-port-xxhdpi") fun foldableCover() = run("foldable-cover")
    @Test @Config(sdk = [34], qualifiers = "w673dp-h841dp-port-xhdpi") fun foldableUnfolded() = run("foldable-unfolded")

    /** The block board from the user's report: one first-strike attacker, three Rabbit tokens to block with. */
    private fun blockScenario(name: String) {
        val message = fake.stateMessage
        val state = message.getJSONObject("state")
        val me = state.getString("viewingPlayerId")
        val cards = state.getJSONObject("cards")
        val ids = cards.keys().asSequence().filter { it.startsWith("dp-") }.toList()
        val mine = ids.filter { cards.getJSONObject(it).getString("controllerId") == me }.take(3)
        val theirs = ids.filter { cards.getJSONObject(it).getString("controllerId") != me }.take(2)
        mine.forEach { cards.getJSONObject(it).put("name", "Rabbit Token").put("typeLine", "Token Creature — Rabbit").put("power", 1).put("toughness", 1)
            .put("isToken", true).put("isTapped", false).put("imageUri", JSONObject.NULL).put("keywords", JSONArray()) }
        theirs.forEachIndexed { i, id -> cards.getJSONObject(id).put("name", if (i == 0) "Ramosian Captain" else "Goblin Raider").put("typeLine", "Creature")
            .put("power", 2).put("toughness", if (i == 0) 2 else 1).put("isAttacking", true).put("isTapped", true).put("imageUri", JSONObject.NULL)
            .put("keywords", if (i == 0) JSONArray().put("FIRST_STRIKE") else JSONArray()) }
        val block = JSONObject().put("actionType", "DeclareBlockers").put("description", "Declare blockers")
            .put("action", JSONObject().put("type", "DeclareBlockers").put("playerId", me).put("blockers", JSONObject()))
            .put("validBlockers", JSONArray(mine))
        message.put("legalActions", JSONArray().put(block)).put("nextStopPoint", JSONObject.NULL)
        state.put("activePlayerId", state.getJSONArray("players").getJSONObject(0).getString("playerId")).put("currentStep", "DECLARE_BLOCKERS")
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        UiTestSupport.settle(rule)
        UiTestSupport.playWithArgentum(rule)
        UiTestSupport.awaitReal(rule, 30_000, "declare blockers offered") { shown("DECLARE BLOCKERS") }
        UiTestSupport.settle(rule, 800)
        rule.onAllNodesWithText("DECLARE BLOCKERS")[0].performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        UiTestSupport.awaitReal(rule, 5_000, "block board") { shown("YOUR BLOCKERS") }
        UiTestSupport.settle(rule, 600)
        scenario.onActivity { UiTestSupport.screenshot(it, "design-$name-5-block-board-empty") }
        mine.forEach { blocker ->
            rule.onNode(hasTestTag("combat-$blocker")).performClick(); UiTestSupport.settle(rule, 300)
            rule.onNode(hasTestTag("combat-${theirs[0]}")).performClick(); UiTestSupport.settle(rule, 300)
        }
        UiTestSupport.settle(rule, 600)
        scenario.onActivity { UiTestSupport.screenshot(it, "design-$name-6-block-board") }
        assertTrue(shown("Ramosian Captain 2/2 first strike ← 3 Rabbit Tokens (1/1)"))
        scenario.close()
    }

    @Test @Config(sdk = [34], qualifiers = "w411dp-h891dp-port-xxhdpi") fun blockBoardPhone() = blockScenario("phone-portrait")
    @Test @Config(sdk = [34], qualifiers = "w891dp-h411dp-land-xxhdpi") fun blockBoardLandscape() = blockScenario("phone-landscape")
}
