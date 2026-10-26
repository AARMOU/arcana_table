package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.wingedsheep.sdk.model.EntityId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Real localhost TCP sockets, not mocks: host + 3 independently addressed guests. */
class FourPlayerLanTransportTest {
    private val ids = listOf(
        LocalArgentumHost.AI_ID,
        EntityId("offline-ai-2"),
        EntityId("offline-ai-3"),
    )
    private data class Received(val seat: EntityId, val message: JSONObject)

    @Test fun threeGuestSocketsHavePrivateSendChannelsAndRejectFifthPhone() {
        val atHost = LinkedBlockingQueue<Received>()
        val left = LinkedBlockingQueue<EntityId>()
        lateinit var server: ArgentumLanMultiServer
        server = ArgentumLanMultiServer("Four-player host", ids,
            onGuest = { seat, hello ->
                assertEquals("lanHello", hello.getString("type"))
                assertTrue(server.admitGuest(seat))
                assertTrue(server.sendTo(seat, JSONObject()
                    .put("type", "lanSeat").put("seat", seat.value)))
            },
            onGuestMessage = { seat, message -> atHost.put(Received(seat, message)) },
            onGuestLeft = { seat -> left.put(seat) },
        )
        val guests = (0..3).map { ArgentumLanGuest() }
        val inbox = (0..3).map { LinkedBlockingQueue<JSONObject>() }
        try {
            server.start()
            assertTrue("LAN multi-host failed to bind: ${server.bindError}", server.awaitReady(10_000))
            for (i in 0..2) {
                guests[i].onMessage = { msg -> inbox[i].put(msg) }
                guests[i].connect("127.0.0.1", JSONObject().put("type", "lanHello").put("name", "Guest $i"))
                val ack = inbox[i].poll(5, TimeUnit.SECONDS)
                assertEquals(ids[i].value, ack?.optString("seat"))
                assertEquals(3 - (i + 1), server.openSeats)
            }
            for (i in 0..2) {
                assertTrue(guests[i].send(JSONObject().put("type", "submitAction").put("fromPhone", i)))
                val incoming = atHost.poll(5, TimeUnit.SECONDS)
                assertNotNull(incoming)
                assertEquals(ids[i], incoming!!.seat)
                assertEquals(i, incoming.message.getInt("fromPhone"))
            }
            // Only directed sends are possible for private engine updates.
            assertFalse(server.send(JSONObject().put("type", "stateUpdate").put("secret", "broadcast leak")))
            for (i in 0..2) {
                assertTrue(server.sendTo(ids[i], JSONObject().put("type", "stateUpdate")
                    .put("secret", "private $i")))
            }
            for (i in 0..2) {
                val received = inbox[i].poll(5, TimeUnit.SECONDS)
                assertEquals("private $i", received?.optString("secret"))
                assertNull("seat $i must not receive another player's view", inbox[i].poll(150, TimeUnit.MILLISECONDS))
            }
            // The 4th *guest* is the 5th phone including the host.
            guests[3].onMessage = { inbox[3].put(it) }
            guests[3].connect("127.0.0.1", JSONObject().put("type", "lanHello").put("name", "Too many"))
            val full = inbox[3].poll(5, TimeUnit.SECONDS)
            assertEquals("error", full?.optString("type"))
            assertEquals("WIFI", full?.optString("code"))
            assertTrue(full?.optString("message")?.contains("full") == true)

            // Losing seat three cannot close the first or fourth player's connection.
            guests[1].close()
            assertEquals(ids[1], left.poll(5, TimeUnit.SECONDS))
            assertEquals(1, server.openSeats)
            assertTrue(server.sendTo(ids[2], JSONObject().put("type", "ping").put("value", 7)))
            assertEquals(7, inbox[2].poll(5, TimeUnit.SECONDS)?.optInt("value"))
            assertTrue(server.sendTo(ids[0], JSONObject().put("type", "ping").put("value", 8)))
            assertEquals(8, inbox[0].poll(5, TimeUnit.SECONDS)?.optInt("value"))
        } finally {
            guests.forEach(ArgentumLanGuest::close)
            server.close()
        }
    }

    @Test fun invalidGuestIsRejectedWithoutEvictingAnotherGuest() {
        val left = LinkedBlockingQueue<EntityId>()
        lateinit var server: ArgentumLanMultiServer
        server = ArgentumLanMultiServer("Host", ids.take(1),
            onGuest = { seat, hello ->
                if (hello.optString("name") == "bad") server.rejectGuest(seat, "Invalid deck")
                else { assertTrue(server.admitGuest(seat)); server.sendTo(seat, JSONObject().put("type", "lanSeat")) }
            },
            onGuestMessage = { _, _ -> },
            onGuestLeft = { left.put(it) },
        )
        val bad = ArgentumLanGuest()
        val good = ArgentumLanGuest()
        val rejected = LinkedBlockingQueue<JSONObject>()
        val accepted = LinkedBlockingQueue<JSONObject>()
        try {
            server.start()
            assertTrue(server.awaitReady(10_000))
            bad.onMessage = { rejected.put(it) }
            bad.connect("127.0.0.1", JSONObject().put("type", "lanHello").put("name", "bad"))
            assertEquals("Invalid deck", rejected.poll(5, TimeUnit.SECONDS)?.optString("message"))
            val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (server.openSeats != 1 && System.nanoTime() < until) Thread.sleep(20)
            assertEquals("bad deck must release its slot", 1, server.openSeats)
            assertNull("unadmitted guest disconnect must not concede anything", left.poll(200, TimeUnit.MILLISECONDS))
            good.onMessage = { accepted.put(it) }
            good.connect("127.0.0.1", JSONObject().put("type", "lanHello").put("name", "good"))
            assertEquals("lanSeat", accepted.poll(5, TimeUnit.SECONDS)?.optString("type"))
        } finally {
            bad.close()
            good.close()
            server.close()
        }
    }
}
