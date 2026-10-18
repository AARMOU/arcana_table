package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.LocalSeat
import com.arcanatable.engine.OfflineCardLibrary
import com.wingedsheep.engine.registry.CardRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * The offline lag fix: max X is worked out only when asked ("refineMaxX" → "maxXRefined"), updates
 * report "engineMs", and a submit made against an older update is turned down, not applied.
 */
class OfflineLagFixTest {
    private fun host(you: List<String>, ai: List<String>, names: Set<String>, inbox: LinkedBlockingQueue<JSONObject>): LocalArgentumHost {
        val library = OfflineCardLibrary({ path -> File("src/main/assets/$path").takeIf { it.exists() }?.inputStream() })
        val registry = CardRegistry().apply { register(library.load(names)) }
        return LocalArgentumHost(registry, LocalSeat("You", you, null), LocalSeat("AI", ai, null), paceMs = 0) { inbox.put(it) }
    }

    private fun actions(msg: JSONObject): List<JSONObject> {
        val legal = msg.optJSONArray("legalActions") ?: JSONArray()
        return (0 until legal.length()).map { legal.getJSONObject(it) }
    }

    private fun passLike(actions: List<JSONObject>) =
        actions.firstOrNull { it.getJSONObject("action").optString("type") in setOf("PassPriority", "DeclareAttackers", "DeclareBlockers") }

    @Test fun staleSubmitIsTurnedDownAndUpdatesReportEngineTime() {
        val inbox = LinkedBlockingQueue<JSONObject>()
        val host = host(List(40) { "Mountain" }, List(40) { "Mountain" }, setOf("Mountain"), inbox)
        host.receive(JSONObject().put("type", "connect"))
        host.receive(JSONObject().put("type", "createQuickGameLobby"))
        var epoch: String? = null
        var pass: JSONObject? = null
        var sawEngineMs = false
        var guard = 0
        while (pass == null && guard++ < 200) {
            val msg = inbox.poll(60, TimeUnit.SECONDS) ?: break
            when (msg.optString("type")) {
                "mulliganDecision" -> host.receive(JSONObject().put("type", "keepHand"))
                "stateUpdate" -> {
                    if (msg.has("engineMs")) sawEngineMs = true
                    passLike(actions(msg))?.let { pass = it.getJSONObject("action"); epoch = msg.getString("interactionEpoch") }
                }
            }
        }
        assertTrue("got an update you could act on", pass != null && epoch != null)
        assertTrue("updates carry engineMs", sawEngineMs)
        assertTrue("epoch is per update", epoch!!.startsWith("offline-") && epoch != "offline-0")
        host.receive(JSONObject().put("type", "submitAction").put("action", pass).put("interactionEpoch", "offline-0"))
        val reply = inbox.poll(30, TimeUnit.SECONDS)
        assertEquals("error", reply?.optString("type"))
        assertTrue(reply!!.optString("message").contains("moved on"))
        host.receive(JSONObject().put("type", "submitAction").put("action", pass).put("interactionEpoch", epoch))
        var accepted = false
        guard = 0
        while (!accepted && guard++ < 50) {
            val msg = inbox.poll(30, TimeUnit.SECONDS) ?: break
            if (msg.optString("type") == "error") break
            if (msg.optString("type") == "stateUpdate") accepted = true
        }
        host.shutdown()
        assertTrue("the current epoch is accepted", accepted)
    }

    @Test fun maxXIsRefinedOnlyWhenAsked() {
        val inbox = LinkedBlockingQueue<JSONObject>()
        val host = host(List(30) { "Mountain" } + List(10) { "Earthquake" }, List(40) { "Mountain" }, setOf("Mountain", "Earthquake"), inbox)
        host.receive(JSONObject().put("type", "connect"))
        host.receive(JSONObject().put("type", "createQuickGameLobby"))
        fun send(action: JSONObject, epoch: String) = host.receive(JSONObject().put("type", "submitAction").put("action", action).put("interactionEpoch", epoch))
        var asked: Pair<String, Int>? = null
        var refined: JSONObject? = null
        var guard = 0
        while (refined == null && guard++ < 600) {
            val msg = inbox.poll(60, TimeUnit.SECONDS) ?: break
            when (msg.optString("type")) {
                "mulliganDecision" -> host.receive(JSONObject().put("type", "keepHand"))
                "maxXRefined" -> refined = msg
                "stateUpdate" -> {
                    if (asked != null) continue
                    val list = actions(msg)
                    if (list.isEmpty()) continue
                    val epoch = msg.getString("interactionEpoch")
                    val quake = list.firstOrNull { it.optBoolean("hasXCost") && it.getJSONObject("action").optString("type") == "CastSpell" && it.optInt("maxAffordableX", 0) >= 2 }
                    val land = list.firstOrNull { it.getJSONObject("action").optString("type") == "PlayLand" }
                    when {
                        quake != null -> {
                            val cardId = quake.getJSONObject("action").getString("cardId")
                            asked = cardId to quake.getInt("maxAffordableX")
                            host.receive(JSONObject().put("type", "refineMaxX").put("cardId", cardId))
                        }
                        land != null -> send(land.getJSONObject("action"), epoch)
                        else -> passLike(list)?.let { send(it.getJSONObject("action"), epoch) }
                    }
                }
            }
        }
        host.shutdown()
        assertTrue("reached a castable Earthquake", asked != null)
        assertTrue("got maxXRefined", refined != null)
        assertEquals(asked!!.first, refined!!.getString("cardId"))
        // No Treasures here, so the engine's number already is what auto-pay can pay.
        assertEquals(asked!!.second, refined!!.getInt("maxX"))
    }
}
