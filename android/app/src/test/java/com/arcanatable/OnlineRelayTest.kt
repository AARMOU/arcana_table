package com.arcanatable

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Online play through the room service. The live part runs only with ARCANA_RELAY_URL set (e.g. a local `wrangler dev`). */
class OnlineRelayTest {
    @Test fun packingRoundTrips() {
        val big = JSONObject().put("type", "stateUpdate").put("blob", "x".repeat(200_000))
        val packed = ArgentumCloudLink.pack(big)
        assert(packed.length < 20_000) { "compressed ${packed.length}" }
        assertEquals(big.toString(), ArgentumCloudLink.unpack(packed).toString())
        assertEquals("wss://a.b.dev/room/ABC234", ArgentumCloudLink.roomUrl("https://a.b.dev/", "abc-234"))
    }

    @Test fun hostAndGuestTalkThroughTheRelay() {
        val url = System.getenv("ARCANA_RELAY_URL")
        assumeTrue("set ARCANA_RELAY_URL to run", !url.isNullOrBlank())
        val hellos = LinkedBlockingQueue<JSONObject>()
        val atHost = LinkedBlockingQueue<JSONObject>()
        val atGuest = LinkedBlockingQueue<JSONObject>()
        val left = LinkedBlockingQueue<Boolean>()
        val host = ArgentumCloudHost(url!!, "Host", onGuest = { hellos.put(it) }, onGuestMessage = { atHost.put(it) }, onGuestLeft = { left.put(true) }, onFailed = { throw AssertionError(it) })
        host.start()
        Thread.sleep(1_500)
        val guest = ArgentumCloudGuest(url, host.code.lowercase(), "Guest")
        guest.onMessage = { atGuest.put(it) }
        val joined = LinkedBlockingQueue<String>()
        guest.connect(JSONObject().put("type", "lanHello").put("name", "Guest")) { joined.put(it ?: "ok") }
        assertEquals("ok", joined.poll(10, TimeUnit.SECONDS))
        assertEquals("Guest", hellos.poll(10, TimeUnit.SECONDS)?.optString("name"))
        // A burst both ways, including a big message.
        repeat(5) { host.send(JSONObject().put("type", "stateUpdate").put("n", it).put("pad", "y".repeat(50_000))) }
        repeat(3) { guest.send(JSONObject().put("type", "submitAction").put("n", it)) }
        val got = (0 until 5).map { atGuest.poll(10, TimeUnit.SECONDS)?.optInt("n") }
        assertEquals(listOf(0, 1, 2, 3, 4), got)
        assertEquals(listOf(0, 1, 2), (0 until 3).map { atHost.poll(10, TimeUnit.SECONDS)?.optInt("n") })
        guest.close()
        assertNotNull("host saw the guest leave", left.poll(10, TimeUnit.SECONDS))
        host.close()
    }
}
