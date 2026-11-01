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
        // Full is not enough: every seated guest must mark ready first.
        assertNull(room.start())
        ids.forEach { assertTrue(room.setReady(it, true)) }

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
        assertTrue(room.setReady(first!!, true))
        assertTrue(room.setReady(second!!, true))
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
        assertTrue(room.setReady(first, true))
        assertTrue(room.setReady(second, true))
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
    @Test fun startWaitsForEveryGuestToMarkReadyAndReadyCanBeWithdrawn() {
        val room = lobby(3)
        room.setKind(1, ArgentumWaitingLobby.Kind.FRIEND)
        room.setKind(2, ArgentumWaitingLobby.Kind.FRIEND)
        val ana = room.seatGuest(seat("Ana", "Forest"))!!
        val ben = room.seatGuest(seat("Ben", "Island"))!!
        assertTrue(room.ready())
        assertFalse(room.canStart())
        assertEquals(listOf("Ana", "Ben"), room.waitingForReady())
        assertTrue(room.setReady(ana, true))
        assertEquals(listOf("Ben"), room.waitingForReady())
        assertNull(room.start())
        assertTrue(room.setReady(ben, true))
        assertTrue(room.canStart())
        assertTrue(room.setReady(ana, false))
        assertFalse(room.canStart())
        assertNull(room.start())
        assertTrue(room.setReady(ana, true))
        assertNotNull(room.start())
        assertFalse(room.setReady(ana, false))
    }

    @Test fun onlySeatedGuestsCanMarkReady() {
        val room = lobby(3)
        room.setKind(1, ArgentumWaitingLobby.Kind.FRIEND)
        assertFalse(room.setReady(LocalArgentumHost.AI_ID, true))
        assertFalse(room.setReady(LocalArgentumHost.HUMAN_ID, true))
        assertFalse(room.setReady(EntityId("offline-ai-2"), true))
        val ana = room.seatGuest(seat("Ana", "Forest"))!!
        assertTrue(room.setReady(ana, true))
        assertTrue(room.canStart())
    }

    @Test fun removedGuestLosesReadyAndTheSeatReopens() {
        val room = lobby(2)
        room.setKind(1, ArgentumWaitingLobby.Kind.FRIEND)
        val ana = room.seatGuest(seat("Ana", "Forest"))!!
        room.setReady(ana, true)
        assertTrue(room.canStart())
        assertTrue(room.removeGuest(ana))
        assertFalse(room.isSeated(ana))
        assertFalse(room.isReady(ana))
        assertEquals(1, room.openFriendSeats())
        assertFalse(room.canStart())
        val ben = room.seatGuest(seat("Ben", "Island"))!!
        assertEquals(ana, ben)
        assertFalse(room.isReady(ben))
        assertFalse(room.canStart())
    }

    @Test fun hostChangesOpenSeatsAndSizeButNeverDropsASeatedGuest() {
        val room = lobby(4)
        room.setKind(1, ArgentumWaitingLobby.Kind.FRIEND)
        room.setKind(2, ArgentumWaitingLobby.Kind.FRIEND)
        val second = EntityId("offline-ai-2")
        assertTrue(room.seatGuestAt(second, seat("Ana", "Forest")))
        assertEquals(listOf("friend", "friend", "ai"), room.transportKinds())

        assertFalse(room.canResize(2))
        assertFalse(room.resize(2))
        assertTrue(room.resize(3))
        assertEquals(3, room.seatCount)
        assertEquals(3, room.lobbyMessage().getJSONArray("seats").length())
        assertEquals(listOf("friend", "friend"), room.transportKinds())

        assertFalse(room.setKind(2, ArgentumWaitingLobby.Kind.AI))
        assertTrue(room.setKind(1, ArgentumWaitingLobby.Kind.AI))
        assertEquals(listOf("ai", "friend"), room.transportKinds())
        assertEquals(0, room.openFriendSeats())

        assertTrue(room.resize(4))
        assertTrue(room.setKind(3, ArgentumWaitingLobby.Kind.FRIEND))
        assertEquals(listOf("ai", "friend", "friend"), room.transportKinds())
        assertEquals(1, room.openFriendSeats())
        assertFalse(room.resize(5))
        assertFalse(room.resize(1))

        assertTrue(room.setKind(3, ArgentumWaitingLobby.Kind.AI))
        assertTrue(room.setReady(second, true))
        val roster = room.start()!!
        assertEquals(listOf("Host", "AI 2", "Ana", "AI 4"), roster.players.map(LocalSeat::name))
        assertEquals(setOf(second), roster.humanGuests.keys)
        assertFalse(room.resize(3))
    }

    @Test fun activeGameLobbyExposesOnlyTheCurrentShareableRoomCode() {
        // The code remains on ArgentumCloudMultiHost even after the waiting-lobby
        // field is cleared at game start. Never use an unrelated guest code.
        assertEquals("GOOSE1", activeGameRoomCode("online-host", "goose1", "OTHER1"))
        assertEquals("OTHER1", activeGameRoomCode("online-guest", "GOOSE1", "other1"))
        assertNull(activeGameRoomCode("online-host", null, "OTHER1"))
        assertNull(activeGameRoomCode("online-guest", "GOOSE1", ""))
        assertNull(activeGameRoomCode("host", "GOOSE1", "OTHER1"))
        assertNull(activeGameRoomCode(null, "GOOSE1", "OTHER1"))
        // Never mistake a host invitation or other long secret for a room locator.
        assertNull(activeGameRoomCode("online-host", "GOOSE1.secret-capability", "OTHER1"))
        assertNull(activeGameRoomCode("online-host", "https://example.test", "OTHER1"))
    }

    @Test fun lobbyMessageCarriesReadinessWithoutDecks() {
        val room = lobby(3)
        room.setKind(1, ArgentumWaitingLobby.Kind.FRIEND)
        room.setKind(2, ArgentumWaitingLobby.Kind.FRIEND)
        val ana = room.seatGuest(seat("Ana", "SecretForestCard"))!!
        var body = room.lobbyMessage()
        var seats = body.getJSONArray("seats")
        assertTrue(seats.getJSONObject(0).getBoolean("ready"))
        assertFalse(seats.getJSONObject(1).getBoolean("ready"))
        assertFalse(seats.getJSONObject(2).getBoolean("ready"))
        assertEquals("Ana", body.getJSONArray("notReady").getString(0))
        assertFalse(body.getBoolean("canStart"))
        assertEquals(listOf(2, 3, 4), (0 until body.getJSONArray("canResize").length()).map { body.getJSONArray("canResize").getInt(it) })
        room.setReady(ana, true)
        body = room.lobbyMessage()
        seats = body.getJSONArray("seats")
        assertTrue(seats.getJSONObject(1).getBoolean("ready"))
        assertEquals(0, body.getJSONArray("notReady").length())
        assertFalse(body.toString().contains("SecretForestCard"))
        assertFalse(body.toString().contains("deck", ignoreCase = true))
    }
}
