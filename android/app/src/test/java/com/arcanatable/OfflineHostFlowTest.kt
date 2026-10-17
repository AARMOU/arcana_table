package com.arcanatable

import com.arcanatable.engine.EmbeddedEngineTrial
import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.LocalSeat
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.Color
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/** Offline play like the server: mulligans, undo, and the AI's turn shown step by step. */
class OfflineHostFlowTest {
    private val cards = File("src/main/assets/argentum/por-cards.jsonl").bufferedReader().useLines { EmbeddedEngineTrial.loadCards(it) }
    private val inbox = LinkedBlockingQueue<JSONObject>()

    private fun host(seed: Int = 3): LocalArgentumHost {
        val registry = CardRegistry().apply { register(cards) }
        val random = Random(seed)
        val you = LocalSeat("You", EmbeddedEngineTrial.monoDeck(cards, Color.GREEN, random).cards, null)
        val ai = LocalSeat("Offline AI", EmbeddedEngineTrial.monoDeck(cards, Color.RED, random).cards, null)
        return LocalArgentumHost(registry, you, ai, paceMs = 0) { inbox.put(it) }.also {
            it.receive(JSONObject().put("type", "connect"))
            it.receive(JSONObject().put("type", "createQuickGameLobby"))
        }
    }

    private fun next(type: String): JSONObject {
        while (true) {
            val msg = inbox.poll(60, TimeUnit.SECONDS) ?: error("no $type message")
            if (msg.optString("type") == type) return msg
        }
    }

    private fun action(host: LocalArgentumHost, action: JSONObject) =
        host.receive(JSONObject().put("type", "submitAction").put("action", action).put("interactionEpoch", "offline"))

    @Test fun londonMulliganDownToSix() {
        val host = host()
        val first = next("mulliganDecision")
        assertEquals(0, first.getInt("mulliganCount"))
        assertEquals(7, first.getJSONArray("hand").length())
        assertNotNull(first.getJSONObject("cards").optJSONObject(first.getJSONArray("hand").getString(0))?.optString("name"))
        host.receive(JSONObject().put("type", "mulligan"))
        val second = next("mulliganDecision")
        assertEquals(1, second.getInt("mulliganCount"))
        assertEquals(1, second.getInt("cardsToPutOnBottom"))
        host.receive(JSONObject().put("type", "keepHand"))
        val bottom = next("chooseBottomCards")
        assertEquals(1, bottom.getInt("cardsToPutOnBottom"))
        host.receive(JSONObject().put("type", "chooseBottomCards").put("cardIds", JSONArray().put(bottom.getJSONArray("hand").getString(0))))
        assertEquals(6, next("mulliganComplete").getInt("finalHandSize"))
        next("stateUpdate")
        host.shutdown()
    }

    @Test fun playingALandCanBeUndone() {
        val host = host()
        next("mulliganDecision"); host.receive(JSONObject().put("type", "keepHand"))
        var state: JSONObject
        var playLand: JSONObject? = null
        var guard = 0
        // Pass until it's our main phase with a land to play.
        while (playLand == null && guard++ < 200) {
            state = next("stateUpdate")
            val legal = state.optJSONArray("legalActions") ?: JSONArray()
            val actions = (0 until legal.length()).map { legal.getJSONObject(it).getJSONObject("action") }
            playLand = actions.firstOrNull { it.optString("type") == "PlayLand" }
            if (playLand == null) actions.firstOrNull { it.optString("type") in setOf("PassPriority", "DeclareAttackers", "DeclareBlockers") }?.let { action(host, it) }
        }
        assertNotNull("reached a main phase with a land in hand", playLand)
        val handBefore = handSize(playLandState(host, playLand!!, undoExpected = true))
        host.receive(JSONObject().put("type", "requestUndo"))
        val afterUndo = next("stateUpdate")
        assertFalse(afterUndo.getBoolean("undoAvailable"))
        assertEquals(handBefore + 1, handSize(afterUndo))
        host.shutdown()
    }

    private fun playLandState(host: LocalArgentumHost, playLand: JSONObject, undoExpected: Boolean): JSONObject {
        action(host, playLand)
        val after = next("stateUpdate")
        assertEquals(undoExpected, after.getBoolean("undoAvailable"))
        return after
    }

    private fun handSize(update: JSONObject): Int {
        val state = update.getJSONObject("state")
        val you = state.optString("viewingPlayerId")
        val zones = state.getJSONArray("zones")
        return (0 until zones.length()).map { zones.getJSONObject(it) }
            .first { it.getJSONObject("zoneId").optString("zoneType").uppercase() == "HAND" && it.getJSONObject("zoneId").optString("ownerId") == you }
            .optInt("size")
    }

    @Test fun theAiTurnIsShownStepByStep() {
        val host = host(seed = 4)
        next("mulliganDecision"); host.receive(JSONObject().put("type", "keepHand"))
        val aiTurnUpdates = mutableMapOf<Int, Int>()
        var turns = 0
        var guard = 0
        while (turns < 6 && guard++ < 2_000) {
            val msg = inbox.poll(60, TimeUnit.SECONDS) ?: break
            if (msg.optString("type") == "gameOver") break
            if (msg.optString("type") != "stateUpdate") continue
            val state = msg.getJSONObject("state")
            val turn = state.optInt("turnNumber")
            turns = maxOf(turns, turn)
            if (state.optString("activePlayerId") != state.optString("viewingPlayerId")) aiTurnUpdates.merge(turn, 1, Int::plus)
            val legal = msg.optJSONArray("legalActions") ?: JSONArray()
            val actions = (0 until legal.length()).map { legal.getJSONObject(it).getJSONObject("action") }
            val choice = actions.firstOrNull { it.optString("type") == "PlayLand" }
                ?: actions.firstOrNull { it.optString("type") in setOf("PassPriority", "DeclareAttackers", "DeclareBlockers") }
            if (choice != null) action(host, choice)
            else if (!msg.isNull("pendingDecision")) {
                val d = msg.getJSONObject("pendingDecision")
                val options = d.optJSONArray("options") ?: JSONArray()
                val picks = JSONArray().apply { for (i in 0 until d.optInt("minSelections")) put(options.getString(i)) }
                action(host, JSONObject().put("type", "SubmitDecision").put("playerId", d.getString("playerId"))
                    .put("response", JSONObject().put("type", "CardsSelectedResponse").put("decisionId", d.getString("id")).put("selectedCards", picks)))
            }
        }
        host.shutdown()
        println("[offline-flow] AI turn updates by turn: $aiTurnUpdates")
        assertTrue("saw AI turns", aiTurnUpdates.isNotEmpty())
        assertTrue("each AI turn shows its steps: $aiTurnUpdates", aiTurnUpdates.values.all { it >= 3 })
    }
}
