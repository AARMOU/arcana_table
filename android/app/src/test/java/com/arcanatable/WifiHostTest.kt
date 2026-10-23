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

/** Wi-Fi play: two people through one engine host, and the line-JSON link over a real socket. */
class WifiHostTest {
    private data class Inbound(val seat: String, val msg: JSONObject)

    @Test fun twoPeoplePlayThroughOneHost() {
        val library = OfflineCardLibrary({ path -> File("src/main/assets/$path").takeIf { it.exists() }?.inputStream() })
        val registry = CardRegistry().apply { register(library.load(setOf("Mountain", "Forest"))) }
        val inbox = LinkedBlockingQueue<Inbound>()
        val host = LocalArgentumHost(registry, LocalSeat("Host", List(40) { "Mountain" }, null), LocalSeat("unused AI", List(40) { "Forest" }, null), paceMs = 0) { inbox.put(Inbound("host", it)) }
        val guestId = LocalArgentumHost.GUEST_ID
        host.setGuest(LocalSeat("Guest", List(40) { "Forest" }, null)) { inbox.put(Inbound("guest", it)) }
        host.receive(JSONObject().put("type", "connect"))
        host.receive(JSONObject().put("type", "createQuickGameLobby"))
        val mulligans = mutableSetOf<String>()
        val interactive = mutableMapOf("host" to 0, "guest" to 0)
        val landsPlayed = mutableMapOf("host" to 0, "guest" to 0)
        var guestConnected = false
        var turn = 0
        var guard = 0
        while (turn < 5 && guard++ < 2_000) {
            val (seat, msg) = inbox.poll(60, TimeUnit.SECONDS) ?: break
            val from = if (seat == "host") LocalArgentumHost.HUMAN_ID else guestId
            when (msg.optString("type")) {
                "connected" -> if (seat == "guest") guestConnected = msg.optString("playerId") == guestId.value
                "mulliganDecision" -> { mulligans += seat; host.receive(JSONObject().put("type", "keepHand"), from) }
                "error" -> throw AssertionError("$seat got error: ${msg.optString("message")}")
                "stateUpdate" -> {
                    turn = maxOf(turn, msg.getJSONObject("state").optInt("turnNumber"))
                    val legal = msg.optJSONArray("legalActions") ?: JSONArray()
                    val actions = (0 until legal.length()).map { legal.getJSONObject(it) }
                    if (actions.isEmpty()) continue
                    interactive[seat] = interactive.getValue(seat) + 1
                    val epoch = msg.getString("interactionEpoch")
                    val land = actions.firstOrNull { it.getJSONObject("action").optString("type") == "PlayLand" }
                    val pick = land ?: actions.firstOrNull { it.getJSONObject("action").optString("type") in setOf("PassPriority", "DeclareAttackers", "DeclareBlockers") } ?: continue
                    if (land != null) landsPlayed[seat] = landsPlayed.getValue(seat) + 1
                    assertEquals("each seat only gets its own actions", from.value, pick.getJSONObject("action").optString("playerId"))
                    host.receive(JSONObject().put("type", "submitAction").put("action", pick.getJSONObject("action")).put("interactionEpoch", epoch), from)
                }
            }
        }
        host.shutdown()
        println("[wifi-host] turn=$turn interactive=$interactive lands=$landsPlayed mulligans=$mulligans")
        assertTrue("guest got its own connected", guestConnected)
        assertEquals("both people choose their opening hand", setOf("host", "guest"), mulligans)
        assertTrue("game reached turn 5", turn >= 5)
        assertTrue("both seats could act", interactive.values.all { it > 0 })
        assertTrue("both seats played lands", landsPlayed.values.all { it >= 2 })
    }

    @Test fun lanLinkCarriesMessagesBothWays() {
        val atHost = LinkedBlockingQueue<JSONObject>()
        val atGuest = LinkedBlockingQueue<JSONObject>()
        val hellos = LinkedBlockingQueue<JSONObject>()
        val left = LinkedBlockingQueue<Boolean>()
        val server = ArgentumLanServer("Host", onGuest = { hellos.put(it) }, onGuestMessage = { atHost.put(it) }, onGuestLeft = { left.put(true) })
        server.start()
        Thread.sleep(300)
        val guest = ArgentumLanGuest()
        guest.onMessage = { atGuest.put(it) }
        guest.connect("127.0.0.1", JSONObject().put("type", "lanHello").put("name", "Guest").put("deck", JSONArray(listOf("Forest"))))
        val hello = hellos.poll(5, TimeUnit.SECONDS)
        assertEquals("Guest", hello?.optString("name"))
        guest.send(JSONObject().put("type", "submitAction").put("n", 1))
        assertEquals(1, atHost.poll(5, TimeUnit.SECONDS)?.optInt("n"))
        server.send(JSONObject().put("type", "stateUpdate").put("n", 2))
        assertEquals(2, atGuest.poll(5, TimeUnit.SECONDS)?.optInt("n"))
        guest.close()
        assertEquals(true, left.poll(5, TimeUnit.SECONDS))
        server.close()
    }
}
