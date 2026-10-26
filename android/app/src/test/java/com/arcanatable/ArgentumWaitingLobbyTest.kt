package com.arcanatable

import com.arcanatable.engine.LocalArgentumHost
import com.arcanatable.engine.LocalSeat
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** No socket or runtime JAR changes: prove admission before wiring to the actual Wi-Fi host. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class ArgentumWaitingLobbyTest {
    private fun seat(name: String, card: String) = LocalSeat(name, List(40) { card }, null)
    private fun lobby(size: Int) = ArgentumWaitingLobby(
        seat("Host", "Mountain"),
        listOf(seat("AI 2", "Forest"), seat("AI 3", "Island"), seat("AI 4", "Swamp")),
        size,
    )

    @Test fun fourFriendsJoinStableSeatsAndCannotOverfillOrStartEarly() {
        val room = lobby(4)
        assertTrue(room.setKind(1, ArgentumWaitingLobby.Kind.FRIEND))
        assertTrue(room.setKind(2, ArgentumWaitingLobby.Kind.FRIEND))
        assertTrue(room.setKind(3, ArgentumWaitingLobby.Kind.FRIEND))
        assertFalse(room.ready())
        assertNull(room.start())
        assertEquals(3, room.openFriendSeats())

        val ids = listOf(
            LocalArgentumHost.AI_ID,
            EntityId("offline-ai-2"),
            EntityId("offline-ai-3"),
        )
        for ((i, id) in ids.withIndex()) {
            assertEquals(id, room.seatGuest(seat("Guest ${i + 1}", listOf("Forest", "Island", "Swamp")[i])))
        }
        assertEquals(0, room.openFriendSeats())
        assertTrue(room.ready())
        assertNull(room.seatGuest(seat("Fifth Phone", "Plains")))

        val roster = room.start()
        assertNotNull(roster)
        assertEquals(4, roster!!.players.size)
        assertEquals(ids.toSet(), roster.humanGuests.keys)
        assertEquals("Guest 3", roster.players[3].name)
        assertNull(room.start())
        assertFalse(room.setKind(2, ArgentumWaitingLobby.Kind.AI))
        assertFalse(room.guestLeft(ids[1]))
    }

    @Test fun aiSeatCanRemainBetweenTwoIndependentHumanGuests() {
        val room = lobby(4)
        assertTrue(room.setKind(1, ArgentumWaitingLobby.Kind.FRIEND))
        assertTrue(room.setKind(3, ArgentumWaitingLobby.Kind.FRIEND))
        val first = room.seatGuest(seat("A", "Forest"))
        val second = room.seatGuest(seat("B", "Swamp"))
        assertEquals(LocalArgentumHost.AI_ID, first)
        assertEquals(EntityId("offline-ai-3"), second)
        assertTrue(room.ready())
        val roster = room.start()!!
        assertEquals(listOf("Host", "A", "AI 3", "B"), roster.players.map(LocalSeat::name))
        assertEquals(listOf("AI 3", "B"), roster.extraOpponents.map(LocalSeat::name))
        assertEquals(setOf(first, second), roster.humanGuests.keys)
    }

    @Test fun disconnectBeforeStartReopensOnlyTheDepartedSeat() {
        val room = lobby(3)
        room.setKind(1, ArgentumWaitingLobby.Kind.FRIEND)
        room.setKind(2, ArgentumWaitingLobby.Kind.FRIEND)
        val first = room.seatGuest(seat("A", "Forest"))!!
        val second = room.seatGuest(seat("B", "Island"))!!
        assertTrue(room.guestLeft(second))
        assertFalse(room.ready())
        assertEquals(1, room.openFriendSeats())
        assertEquals(second, room.seatGuest(seat("Replacement B", "Swamp")))
        assertTrue(room.ready())
        assertEquals("A", room.start()!!.humanGuests[first]?.name)
    }

    @Test fun invalidOrUnexpectedGuestNeverTakesAnAiSeat() {
        val room = lobby(2)
        assertNull(room.seatGuest(seat("Unexpected", "Forest")))
        room.setKind(1, ArgentumWaitingLobby.Kind.FRIEND)
        assertNull(room.seatGuest(LocalSeat("", List(40) { "Island" }, null)))
        assertNull(room.seatGuest(LocalSeat("Empty deck", emptyList(), null)))
        assertEquals(LocalArgentumHost.AI_ID, room.seatGuest(seat("Good", "Island")))
        assertFalse(room.setKind(1, ArgentumWaitingLobby.Kind.AI))
        assertFalse(room.guestLeft(EntityId("offline-ai-3")))
    }

    @Test fun broadcastLobbyContainsNoDecksAndDoesNotDiscloseUnjoinedAiDeck() {
        val room = lobby(4)
        room.setKind(1, ArgentumWaitingLobby.Kind.FRIEND)
        room.setKind(2, ArgentumWaitingLobby.Kind.FRIEND)
        room.seatGuest(seat("First", "SecretForestCard"))
        val body = room.lobbyMessage()
        val serialized = body.toString()
        assertEquals("lanLobby", body.getString("type"))
        assertEquals(4, body.getInt("seatCount"))
        assertEquals(1, body.getInt("openSeats"))
        assertEquals(4, body.getJSONArray("seats").length())
        assertFalse(serialized.contains("SecretForestCard"))
        assertFalse(serialized.contains("Mountain"))
        assertFalse(serialized.contains("Forest"))
        assertFalse(serialized.contains("Island"))
        assertFalse(serialized.contains("Swamp"))
        assertFalse(serialized.contains("deck", ignoreCase = true))
        assertEquals("waiting", body.getJSONArray("seats").getJSONObject(2).getString("kind"))
        assertEquals("ai", body.getJSONArray("seats").getJSONObject(3).getString("kind"))
    }
}
