package com.arcanatable

import com.wingedsheep.sdk.model.EntityId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** The live test runs against a local wrangler dev URL only when ARCANA_RELAY_URL is set. */
class OnlineFourPlayerRelayTest {
    @Test fun fourOnlineSeatsMapToTheSameArgentumIdsAsWifi() {
        assertEquals("offline-ai", ArgentumCloudMultiHost.seatId(1).value)
        assertEquals("offline-ai-2", ArgentumCloudMultiHost.seatId(2).value)
        assertEquals("offline-ai-3", ArgentumCloudMultiHost.seatId(3).value)
        assertEquals(1, ArgentumCloudMultiHost.seatNumber(EntityId("offline-ai")))
        assertEquals(2, ArgentumCloudMultiHost.seatNumber(EntityId("offline-ai-2")))
        assertEquals(3, ArgentumCloudMultiHost.seatNumber(EntityId("offline-ai-3")))
        assertEquals(null, ArgentumCloudMultiHost.seatNumber(EntityId("offline-you")))
    }

    @Test fun fourPhonesCommunicateWithTheHostButNeverEachOther() {
        val url = System.getenv("ARCANA_RELAY_URL")
        assumeTrue("Set ARCANA_RELAY_URL to a local wrangler dev host", !url.isNullOrBlank())
        val arrived = LinkedBlockingQueue<EntityId>()
        val actions = LinkedBlockingQueue<Pair<EntityId, JSONObject>>()
        val ready = LinkedBlockingQueue<String>()
        val failures = LinkedBlockingQueue<String>()
        val host = ArgentumCloudMultiHost(
            url!!, "Host", listOf("friend", "friend", "friend"),
            onGuest = { id, _ -> arrived.put(id) },
            onGuestMessage = { id, msg -> actions.put(id to msg) },
            onGuestLeft = {},
            onFailed = { failures.put(it) },
        )
        host.onRoomReady = { ready.put("ready") }
        val guests = (1..3).map { index ->
            ArgentumCloudMultiGuest(url, host.code, "Friend $index")
        }
        val views = guests.map { LinkedBlockingQueue<JSONObject>() }
        try {
            host.start()
            assertEquals("Worker seat configuration acknowledged", "ready",
                ready.poll(20, TimeUnit.SECONDS))
            for (i in guests.indices) {
                val joined = LinkedBlockingQueue<String>()
                guests[i].onMessage = { views[i].put(it) }
                guests[i].connect { reason -> joined.put(reason ?: "ok") }
                assertEquals("ok", joined.poll(20, TimeUnit.SECONDS))
                assertTrue(guests[i].send(JSONObject().put("type", "lanHello")
                    .put("name", "Friend ${i + 1}").put("deck", org.json.JSONArray())))
                assertEquals(ArgentumCloudMultiHost.seatId(i + 1),
                    arrived.poll(20, TimeUnit.SECONDS))
            }

            for (index in 1..3) {
                val who = ArgentumCloudMultiHost.seatId(index)
                assertTrue(host.sendTo(who, JSONObject().put("type", "stateUpdate")
                    .put("privateHand", "SECRET_FOR_$index")))
            }
            for (i in guests.indices) {
                val ownView = views[i].poll(20, TimeUnit.SECONDS)
                assertNotNull("seat ${i + 1} received a masked view", ownView)
                assertEquals("SECRET_FOR_${i + 1}", ownView?.optString("privateHand"))
                for (other in 1..3) {
                    if (other != i + 1) {
                        assertFalse("seat ${i + 1} saw seat $other's private view",
                            ownView.toString().contains("SECRET_FOR_$other"))
                    }
                }
                assertEquals(null, views[i].poll(300, TimeUnit.MILLISECONDS))
            }

            for (i in guests.indices) {
                assertTrue(guests[i].send(JSONObject().put("type", "submitAction")
                    .put("sourceSeat", i + 1)))
                val (from, action) = actions.poll(20, TimeUnit.SECONDS)
                    ?: throw AssertionError("Host did not receive seat ${i + 1}")
                assertEquals(ArgentumCloudMultiHost.seatId(i + 1), from)
                assertEquals(i + 1, action.optInt("sourceSeat"))
            }
            assertEquals(null, failures.poll(200, TimeUnit.MILLISECONDS))
        } finally {
            guests.forEach(ArgentumCloudMultiGuest::close)
            host.close()
        }
    }
}
