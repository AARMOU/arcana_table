package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.LocalSeat
import com.arcanatable.engine.OfflineCardLibrary
import com.wingedsheep.engine.registry.CardRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** A shock land fetched with a fetch land asks "pay 2 life to untap it?" offline (Argentum alone enters it tapped). */
class ShockFetchTest {
    @Test fun fetchedShockLandOffersToPayLife() {
        val library = OfflineCardLibrary({ path -> File("src/main/assets/$path").takeIf { it.exists() }?.inputStream() })
        val registry = CardRegistry().apply { register(library.load(setOf("Wooded Foothills", "Stomping Ground", "Mountain"))) }
        val you = LocalSeat("You", List(20) { "Wooded Foothills" } + List(20) { "Stomping Ground" }, null)
        val ai = LocalSeat("AI", List(40) { "Mountain" }, null)
        val inbox = LinkedBlockingQueue<JSONObject>()
        val host = LocalArgentumHost(registry, you, ai, paceMs = 0) { inbox.put(it) }
        host.receive(JSONObject().put("type", "connect"))
        host.receive(JSONObject().put("type", "createQuickGameLobby"))
        fun send(action: JSONObject) = host.receive(JSONObject().put("type", "submitAction").put("action", action).put("interactionEpoch", "offline"))
        var stage = 0
        var lifeAfter = -1
        var untapped = false
        var guard = 0
        while (stage < 4 && guard++ < 300) {
            val msg = inbox.poll(60, TimeUnit.SECONDS) ?: break
            when (msg.optString("type")) {
                "mulliganDecision" -> host.receive(JSONObject().put("type", "keepHand"))
                "stateUpdate" -> {
                    val legal = msg.optJSONArray("legalActions") ?: JSONArray()
                    val actions = (0 until legal.length()).map { legal.getJSONObject(it) }
                    val decision = msg.optJSONObject("pendingDecision")
                    when {
                        decision != null && decision.optString("id").startsWith("arcana-shock-") -> {
                            send(JSONObject().put("type", "SubmitDecision").put("playerId", decision.getString("playerId"))
                                .put("response", JSONObject().put("type", "YesNoResponse").put("decisionId", decision.getString("id")).put("choice", true)))
                            stage = 3
                        }
                        decision != null && decision.optString("type") == "SelectCardsDecision" -> {
                            val pick = decision.getJSONArray("options").getString(0)
                            send(JSONObject().put("type", "SubmitDecision").put("playerId", decision.getString("playerId"))
                                .put("response", JSONObject().put("type", "CardsSelectedResponse").put("decisionId", decision.getString("id")).put("selectedCards", JSONArray().put(pick))))
                            stage = 2
                        }
                        stage == 3 -> {
                            val state = msg.getJSONObject("state")
                            val cards = state.getJSONObject("cards")
                            val zones = state.getJSONArray("zones")
                            val onField = (0 until zones.length()).map { zones.getJSONObject(it) }
                                .filter { it.getJSONObject("zoneId").optString("zoneType").equals("BATTLEFIELD", true) }
                                .flatMap { z -> z.optJSONArray("cardIds")?.let { a -> (0 until a.length()).map(a::getString) }.orEmpty() }
                            untapped = onField.mapNotNull { cards.optJSONObject(it) }.any { it.optString("name") == "Stomping Ground" && !it.optBoolean("isTapped") }
                            val players = state.getJSONArray("players")
                            lifeAfter = (0 until players.length()).map { players.getJSONObject(it) }.first { it.optString("playerId") == LocalArgentumHost.HUMAN_ID.value }.optInt("life")
                            stage = 4
                        }
                        stage == 0 && actions.any { it.optString("description").contains("Wooded Foothills") && it.getJSONObject("action").optString("type") == "PlayLand" } ->
                            send(actions.first { it.optString("description").contains("Wooded Foothills") && it.getJSONObject("action").optString("type") == "PlayLand" }.getJSONObject("action")).also { stage = 1 }
                        stage == 1 && actions.any { it.optString("description").contains("Search your library") } ->
                            send(actions.first { it.optString("description").contains("Search your library") }.getJSONObject("action"))
                        else -> actions.firstOrNull { it.getJSONObject("action").optString("type") in setOf("PassPriority", "DeclareAttackers", "DeclareBlockers") }?.let { send(it.getJSONObject("action")) }
                    }
                }
            }
        }
        host.shutdown()
        println("[shock-fetch] stage=$stage life=$lifeAfter untapped=$untapped")
        assertEquals("reached shock prompt; stage=$stage life=$lifeAfter untapped=$untapped", 4, stage)
        assertEquals("fetch/shock life accounting; stage=$stage life=$lifeAfter untapped=$untapped", 17, lifeAfter)
        assertTrue("Stomping Ground untapped after Yes; stage=$stage life=$lifeAfter untapped=$untapped", untapped)
        assertFalse(lifeAfter < 0)
    }
}
