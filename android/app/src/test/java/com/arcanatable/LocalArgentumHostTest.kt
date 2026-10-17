package com.arcanatable

import com.arcanatable.engine.EmbeddedEngineTrial
import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.LocalSeat
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.Color
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * The offline host speaks Argentum's `/game` messages: Arcana's own parser reads its state, and a
 * client that only ever presses Argentum's PassPriority gets through several turns against the AI.
 */
class LocalArgentumHostTest {
    @Test fun offlineGameRunsThroughArcanasProtocol() {
        val cards = File("src/main/assets/argentum/por-cards.jsonl").bufferedReader().useLines { EmbeddedEngineTrial.loadCards(it) }
        val registry = CardRegistry().apply { register(cards) }
        val random = Random(3)
        val you = LocalSeat("You", EmbeddedEngineTrial.monoDeck(cards, Color.GREEN, random).cards, null)
        val ai = LocalSeat("Offline AI", EmbeddedEngineTrial.monoDeck(cards, Color.RED, random).cards, null)
        val inbox = LinkedBlockingQueue<JSONObject>()
        val host = LocalArgentumHost(registry, you, ai) { inbox.put(it) }
        host.receive(JSONObject().put("type", "connect"))
        host.receive(JSONObject().put("type", "createQuickGameLobby"))
        val types = mutableListOf<String>()
        var lastTurn = 0
        var passes = 0
        var over = false
        var decisions = 0
        while (passes < 400 && !over) {
            val msg = inbox.poll(60, TimeUnit.SECONDS) ?: break
            types += msg.optString("type")
            when (msg.optString("type")) {
                "gameOver" -> over = true
                "stateUpdate" -> {
                    val snapshot = ArgentumTableSnapshot.parse(msg.getJSONObject("state"), msg.optJSONArray("legalActions") ?: JSONArray(), msg.optString("interactionEpoch"))
                    assertEquals(2, snapshot.players.size)
                    lastTurn = snapshot.turnNumber
                    if (snapshot.gameOver) { over = true; continue }
                    val legal = snapshot.legalActions
                    val pass = legal.firstOrNull { it.optJSONObject("action")?.optString("type") == "PassPriority" }
                    val attack = legal.firstOrNull { it.optJSONObject("action")?.optString("type") == "DeclareAttackers" }
                    val block = legal.firstOrNull { it.optJSONObject("action")?.optString("type") == "DeclareBlockers" }
                    val chosen = pass ?: attack ?: block
                    if (chosen != null) {
                        host.receive(JSONObject().put("type", "submitAction").put("action", chosen.getJSONObject("action")).put("interactionEpoch", "offline"))
                        passes++
                    } else if (!msg.isNull("pendingDecision")) {
                        val d = msg.getJSONObject("pendingDecision")
                        if (d.optString("type") != "SelectCardsDecision") { println("[offline-host] stopped at decision ${d.optString("type")}"); break }
                        // Discard to hand size: pick the first cards Argentum offers, exactly as Arcana's dialog would send.
                        val options = d.getJSONArray("options")
                        val picks = JSONArray().apply { for (i in 0 until d.optInt("minSelections")) put(options.getString(i)) }
                        val response = JSONObject().put("type", "CardsSelectedResponse").put("decisionId", d.getString("id")).put("selectedCards", picks)
                        host.receive(JSONObject().put("type", "submitAction").put("interactionEpoch", "offline")
                            .put("action", JSONObject().put("type", "SubmitDecision").put("playerId", d.getString("playerId")).put("response", response)))
                        decisions++
                    }
                }
                "error" -> println("[offline-host] error ${msg.optString("message")}")
            }
        }
        host.shutdown()
        println("[offline-host] messages=${types.size} turn=$lastTurn passes=$passes decisions=$decisions over=$over first=${types.take(4)}")
        assertEquals(listOf("connected", "quickGameLobbyState", "gameStarted", "stateUpdate"), types.take(4))
        assertTrue("game advanced through several turns", lastTurn >= 4 || over)
    }
}
