package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.LocalSeat
import com.arcanatable.engine.OfflineCardLibrary
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.EntityId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Phase 1: four human identities and viewer-masked state on the real Argentum host.
 * This is transport-neutral; the lobby/LAN multi-guest transport are separate phases.
 */
class FourPlayerHumanHostTest {
    private data class Packet(val seat: String, val body: JSONObject)

    private fun seat(name: String, land: String) = LocalSeat(name, List(40) { land }, null)

    @Test fun fourPeopleEachGetTheirOwnMaskedEngineView() {
        val library = OfflineCardLibrary({ path ->
            File("src/main/assets/$path").takeIf(File::exists)?.inputStream()
        })
        val colors = linkedMapOf(
            "host" to "Mountain",
            "second" to "Forest",
            "third" to "Island",
            "fourth" to "Swamp",
        )
        val registry = CardRegistry().apply { register(library.load(colors.values.toSet())) }
        val messages = LinkedBlockingQueue<Packet>()
        val ids = mapOf(
            "host" to LocalArgentumHost.HUMAN_ID,
            "second" to LocalArgentumHost.AI_ID,
            "third" to EntityId("offline-ai-2"),
            "fourth" to EntityId("offline-ai-3"),
        )
        val host = LocalArgentumHost(
            registry, seat("Host", "Mountain"), seat("Second", "Forest"), paceMs = 0,
            extraOpponents = listOf(seat("Third", "Island"), seat("Fourth", "Swamp")),
            emit = { messages.put(Packet("host", it)) },
        )
        try {
            for (remote in listOf("second", "third", "fourth")) {
                host.setGuest(ids.getValue(remote), seat(remote, colors.getValue(remote))) {
                    messages.put(Packet(remote, it))
                }
            }
            host.receive(JSONObject().put("type", "connect"))
            host.receive(JSONObject().put("type", "createQuickGameLobby"))

            val connected = mutableSetOf<String>()
            val mulligans = mutableSetOf<String>()
            val views = mutableMapOf<String, JSONObject>()
            var guard = 0
            while (views.size < 4 && guard++ < 120) {
                val packet = messages.poll(5, TimeUnit.SECONDS) ?: break
                when (packet.body.optString("type")) {
                    "connected" -> {
                        assertEquals(ids.getValue(packet.seat).value, packet.body.getString("playerId"))
                        connected += packet.seat
                    }
                    "mulliganDecision" -> {
                        mulligans += packet.seat
                        host.receive(JSONObject().put("type", "keepHand"), ids.getValue(packet.seat))
                    }
                    "stateUpdate" -> views.putIfAbsent(packet.seat, packet.body.getJSONObject("state"))
                    "error" -> throw AssertionError("${packet.seat}: ${packet.body.optString("message")}")
                }
            }
            assertEquals(setOf("host", "second", "third", "fourth"), connected)
            assertEquals(connected, mulligans)
            assertEquals(connected, views.keys)
            for ((viewer, state) in views) {
                // The server may project shared battlefield cards, but before anyone has played
                // a card, only this viewer's private opening hand may reveal a land name.
                val wire = state.toString()
                assertTrue("viewer $viewer cannot see their own hand", wire.contains(colors.getValue(viewer)))
                for ((other, privateCard) in colors) {
                    if (other != viewer) {
                        assertFalse("$viewer saw $other's unplayed hand/library", wire.contains("\"name\":\"$privateCard\""))
                    }
                }
                assertEquals(4, state.getJSONArray("players").length())
            }

            // Disconnect seat 3 (not the first guest). Seat 2 stays registered, and
            // must still be able to ask for its *own* masked view.
            host.receive(
                JSONObject().put("type", "guestLeft").put("seatId", ids.getValue("third").value),
                ids.getValue("host"),
            )
            host.receive(JSONObject().put("type", "requestResync"), ids.getValue("second"))
            var secondResynced = false
            var hostSawThirdLeave = false
            var leftGuard = 0
            while ((!secondResynced || !hostSawThirdLeave) && leftGuard++ < 90) {
                val packet = messages.poll(5, TimeUnit.SECONDS) ?: break
                if (packet.seat == "second" && packet.body.optString("type") == "stateUpdate") {
                    secondResynced = true
                    assertFalse(packet.body.getJSONObject("state").optBoolean("gameOver"))
                }
                if (packet.seat == "host" && packet.body.optString("type") == "error" &&
                    packet.body.optString("message").contains("third")) hostSawThirdLeave = true
            }
            assertTrue("remaining guest still receives updates", secondResynced)
            assertTrue("departure identifies only the departing seat", hostSawThirdLeave)
        } finally {
            host.shutdown()
        }
    }

    /**
     * A friend who drops between START and the first deal must not leave a "person" seat that
     * never acts: the AI plays it, and the remaining people get their opening hands and views.
     */
    @Test fun guestWhoLeavesBeforeTheDealIsPlayedByTheAi() {
        val library = OfflineCardLibrary({ path ->
            File("src/main/assets/$path").takeIf(File::exists)?.inputStream()
        })
        val registry = CardRegistry().apply { register(library.load(setOf("Mountain", "Forest", "Island", "Swamp"))) }
        val messages = LinkedBlockingQueue<Packet>()
        val ids = mapOf(
            "host" to LocalArgentumHost.HUMAN_ID,
            "second" to LocalArgentumHost.AI_ID,
            "third" to EntityId("offline-ai-2"),
            "fourth" to EntityId("offline-ai-3"),
        )
        val host = LocalArgentumHost(
            registry, seat("Host", "Mountain"), seat("Second", "Forest"), paceMs = 0,
            extraOpponents = listOf(seat("Third", "Island"), seat("Fourth", "Swamp")),
            emit = { messages.put(Packet("host", it)) },
        )
        try {
            host.setGuest(ids.getValue("second"), seat("second", "Forest")) { messages.put(Packet("second", it)) }
            host.setGuest(ids.getValue("third"), seat("third", "Island")) { messages.put(Packet("third", it)) }
            host.setGuest(ids.getValue("fourth"), seat("fourth", "Swamp")) { messages.put(Packet("fourth", it)) }
            host.receive(JSONObject().put("type", "guestLeft").put("seatId", ids.getValue("fourth").value), ids.getValue("host"))
            host.receive(JSONObject().put("type", "connect"))
            host.receive(JSONObject().put("type", "createQuickGameLobby"))

            val views = mutableSetOf<String>()
            var fourthAfterLeaving = 0
            var leftNotice = false
            var guard = 0
            while (views.size < 3 && guard++ < 160) {
                val packet = messages.poll(5, TimeUnit.SECONDS) ?: break
                val type = packet.body.optString("type")
                if (packet.seat == "fourth" && type != "connected") fourthAfterLeaving++
                when (type) {
                    "mulliganDecision" -> host.receive(JSONObject().put("type", "keepHand"), ids.getValue(packet.seat))
                    "stateUpdate" -> views += packet.seat
                    "error" -> if (packet.seat == "host" && packet.body.optString("message").contains("AI is playing")) leftNotice = true
                }
            }
            assertEquals("every remaining person gets a view", setOf("host", "second", "third"), views)
            assertEquals("the departed phone receives nothing after leaving", 0, fourthAfterLeaving)
            assertTrue("host is told the AI took the seat", leftNotice)
        } finally {
            host.shutdown()
        }
    }
}
