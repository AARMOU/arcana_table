package com.arcanatable

import com.arcanatable.engine.EmbeddedEngineTrial
import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.LocalSeat
import com.arcanatable.engine.OfflineCardLibrary
import com.arcanatable.engine.OfflineGameSetup
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

/** Build 107: offline games with up to three AI opponents. */
class FourPlayerOfflineTest {
    private val porCards by lazy { File("src/main/assets/argentum/por-cards.jsonl").bufferedReader().useLines { EmbeddedEngineTrial.loadCards(it) } }

    @Test fun setupMakesThreeAiSeats() {
        val library = OfflineCardLibrary({ path -> File("src/main/assets/$path").takeIf { it.exists() }?.inputStream() }, "por-cards")
        val (setup, _) = OfflineGameSetup.build(library, "You", emptyList(), null, null, Random(1), aiCount = 3)
        assertEquals(2, setup.extraAis.size)
        assertEquals(setOf("AI · Red", "AI · Blue", "AI · Black"), (listOf(setup.ai) + setup.extraAis).map { it.name }.toSet())
        val (duel, _) = OfflineGameSetup.build(library, "You", emptyList(), null, null, Random(1))
        assertEquals(0, duel.extraAis.size)
        assertEquals("Offline AI", duel.ai.name)
    }

    @Test fun fourPlayerGameRunsThroughArcanasProtocol() {
        val registry = CardRegistry().apply { register(porCards) }
        val random = Random(5)
        fun seat(name: String, color: Color) = LocalSeat(name, EmbeddedEngineTrial.monoDeck(porCards, color, random).cards, null)
        val inbox = LinkedBlockingQueue<JSONObject>()
        val host = LocalArgentumHost(registry, seat("You", Color.GREEN), seat("AI · Red", Color.RED), paceMs = 0,
            emit = { inbox.put(it) }, extraOpponents = listOf(seat("AI · Blue", Color.BLUE), seat("AI · Black", Color.BLACK)))
        host.receive(JSONObject().put("type", "connect"))
        host.receive(JSONObject().put("type", "createQuickGameLobby"))
        var lobbyPlayers = 0
        var lastTurn = 0
        var actives = mutableSetOf<Int>()
        var passes = 0
        var over = false
        while (passes < 800 && !over) {
            val msg = inbox.poll(90, TimeUnit.SECONDS) ?: break
            when (msg.optString("type")) {
                "quickGameLobbyState" -> lobbyPlayers = msg.getJSONArray("players").length()
                "mulliganDecision" -> host.receive(JSONObject().put("type", "keepHand"))
                "gameOver" -> over = true
                "stateUpdate" -> {
                    val snapshot = ArgentumTableSnapshot.parse(msg.getJSONObject("state"), msg.optJSONArray("legalActions") ?: JSONArray(), msg.optString("interactionEpoch"))
                    assertEquals(4, snapshot.players.size)
                    lastTurn = snapshot.turnNumber
                    actives += snapshot.activePlayer
                    if (snapshot.gameOver) { over = true; continue }
                    val legal = snapshot.legalActions
                    val chosen = listOf("PassPriority", "DeclareAttackers", "DeclareBlockers").firstNotNullOfOrNull { t -> legal.firstOrNull { it.optJSONObject("action")?.optString("type") == t } }
                    if (chosen != null) {
                        host.receive(JSONObject().put("type", "submitAction").put("action", chosen.getJSONObject("action")).put("interactionEpoch", msg.optString("interactionEpoch")))
                        passes++
                    } else if (!msg.isNull("pendingDecision")) {
                        val d = msg.getJSONObject("pendingDecision")
                        if (d.optString("type") != "SelectCardsDecision") { println("[4p-offline] stopped at decision ${d.optString("type")}"); break }
                        val options = d.getJSONArray("options")
                        val picks = JSONArray().apply { for (i in 0 until d.optInt("minSelections")) put(options.getString(i)) }
                        val response = JSONObject().put("type", "CardsSelectedResponse").put("decisionId", d.getString("id")).put("selectedCards", picks)
                        host.receive(JSONObject().put("type", "submitAction").put("interactionEpoch", msg.optString("interactionEpoch"))
                            .put("action", JSONObject().put("type", "SubmitDecision").put("playerId", d.getString("playerId")).put("response", response)))
                    }
                }
                "error" -> println("[4p-offline] error ${msg.optString("message")}")
            }
        }
        host.shutdown()
        println("[4p-offline] lobby=$lobbyPlayers turn=$lastTurn actives=$actives passes=$passes over=$over")
        assertEquals(4, lobbyPlayers)
        assertTrue("every seat took a turn (saw $actives)", actives.size == 4 || over)
        assertTrue("game advanced through a few rounds", lastTurn >= 8 || over)
    }

    @Test fun concedingEndsTheGameForYouWhileAisPlayOn() {
        val registry = CardRegistry().apply { register(porCards) }
        val random = Random(9)
        fun seat(name: String, color: Color) = LocalSeat(name, EmbeddedEngineTrial.monoDeck(porCards, color, random).cards, null)
        val inbox = LinkedBlockingQueue<JSONObject>()
        val host = LocalArgentumHost(registry, seat("You", Color.GREEN), seat("AI 1", Color.RED), paceMs = 0,
            emit = { inbox.put(it) }, extraOpponents = listOf(seat("AI 2", Color.BLUE), seat("AI 3", Color.BLACK)))
        host.receive(JSONObject().put("type", "connect"))
        host.receive(JSONObject().put("type", "createQuickGameLobby"))
        var gameOver: JSONObject? = null
        var conceded = false
        while (gameOver == null) {
            val msg = inbox.poll(60, TimeUnit.SECONDS) ?: break
            when (msg.optString("type")) {
                "mulliganDecision" -> host.receive(JSONObject().put("type", "keepHand"))
                "stateUpdate" -> if (!conceded) { conceded = true; host.receive(JSONObject().put("type", "concede")) }
                "gameOver" -> gameOver = msg
            }
        }
        host.shutdown()
        assertEquals("YOU_LOST", gameOver?.optString("reason"))
    }
}
