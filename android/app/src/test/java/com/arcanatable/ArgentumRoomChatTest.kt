package com.arcanatable

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.arcanatable.engine.LocalSeat
import com.wingedsheep.sdk.model.EntityId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Sideband room chat must not leak into Argentum actions or trust client-supplied names. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArgentumRoomChatTest {
    private fun seat(name: String) = LocalSeat(name, List(40) { "Forest" }, null)
    private fun room() = ArgentumWaitingLobby(
        seat("Host"),
        listOf(seat("Seat 2 AI"), seat("Seat 3 AI"), seat("Seat 4 AI")), 4
    ).also {
        (1..3).forEach { index -> it.setKind(index, ArgentumWaitingLobby.Kind.FRIEND) }
    }

    private fun controller() = TableController(
        ApplicationProvider.getApplicationContext<Context>(),
        CoroutineScope(SupervisorJob() + Dispatchers.Main),
        CardZoomController(),
    )

    @Test fun payloadsAreBoundedAndNeverAcceptBlankText() {
        assertNull(ArgentumRoomChat.request("   "))
        assertNull(ArgentumRoomChat.request("x".repeat(501)))
        assertEquals("hello", ArgentumRoomChat.request(" hello ")?.optString("text"))
        assertNull(ArgentumRoomChat.requestedText(JSONObject().put("type", "submitAction").put("text", "hi")))
        assertNull(ArgentumRoomChat.requestedText(JSONObject().put("type", ArgentumRoomChat.REQUEST).put("text", "x".repeat(501))))
    }

    @Test fun deliveredPacketsHaveTheirOwnTypeAndStrictFields() {
        val packet = ArgentumRoomChat.delivery(17, 3, "  Alice ", " hi ", "00:12")
        assertEquals(ArgentumRoomChat.DELIVERY, packet.optString("type"))
        assertEquals(ArgentumRoomChat.Message(17, 3, "Alice", "hi", "00:12"),
            ArgentumRoomChat.parseDelivery(packet))
        assertNull(ArgentumRoomChat.parseDelivery(JSONObject().put("type", "stateUpdate")))
        assertNull(ArgentumRoomChat.parseDelivery(JSONObject().put("type", ArgentumRoomChat.DELIVERY)
            .put("id", 0).put("seat", 3).put("author", "Alice").put("text", "hi")))
        assertNull(ArgentumRoomChat.parseDelivery(JSONObject().put("type", ArgentumRoomChat.DELIVERY)
            .put("id", 1).put("seat", 9).put("author", "Alice").put("text", "hi")))
    }

    @Test fun unseatedGuestCannotInjectChat() {
        val t = controller()
        t.wifiMultiLobby = room()
        t.receiveRoomChatFromGuest(EntityId("offline-ai"), JSONObject().put("type", ArgentumRoomChat.REQUEST)
            .put("text", "fake message"))
        assertTrue(t.chatMessages.isEmpty())
    }

    @Test fun hostUsesTheAdmittedSeatNameAndIgnoresSpoofedClientMetadata() {
        val t = controller()
        val room = room()
        val guestId = EntityId("offline-ai-2")
        assertTrue(room.seatGuestAt(guestId, seat("Authenticated Guest")))
        t.wifiMultiLobby = room
        t.receiveRoomChatFromGuest(guestId, JSONObject()
            .put("type", ArgentumRoomChat.REQUEST).put("text", " hello ")
            .put("author", "FORGED HOST").put("seat", 0))
        assertEquals(1, t.chatMessages.size)
        assertEquals("Authenticated Guest", t.chatMessages.single().author)
        assertEquals("hello", t.chatMessages.single().text)
        // The only message accepted is assigned to seat 3, never the forged seat 1.
        assertEquals(1L, t.roomChatNextId)
    }

    @Test fun duplicateRelayDeliveryDoesNotCreateDuplicateMessages() {
        val t = controller()
        val delivery = ArgentumRoomChat.delivery(12L, 2, "Guest", "message", "01:23")
        t.receiveRoomChat(delivery)
        t.receiveRoomChat(delivery)
        assertEquals(1, t.chatMessages.size)
        assertEquals("message", t.chatMessages.single().text)
        t.resetRoomChat()
        assertTrue(t.chatMessages.isEmpty())
        assertEquals(0L, t.roomChatNextId)
    }

    @Test fun hostCanSendWithoutForwardingThroughRulesEngine() {
        val t = controller()
        val room = room()
        t.wifiMultiLobby = room
        t.wifiServer = ArgentumLanMultiServer("Host", room.friendSeatIds(),
            onGuest = { _, _ -> }, onGuestMessage = { _, _ -> }, onGuestLeft = { _ -> })
        try {
            assertTrue(t.sendTableChat("host announcement"))
            assertEquals("Host", t.chatMessages.single().author)
            assertEquals("host announcement", t.chatMessages.single().text)
        } finally {
            t.wifiServer?.close()
        }
    }
}
