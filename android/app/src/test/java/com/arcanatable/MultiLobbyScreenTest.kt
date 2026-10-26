package com.arcanatable

import com.arcanatable.engine.LocalSeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The lobby screen reads exactly what the host's waiting room publishes. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class MultiLobbyScreenTest {
    private fun seat(name: String, commander: String?) = LocalSeat(name, List(40) { "Island" }, commander)
    private fun room() = ArgentumWaitingLobby(
        seat("Host", "Atraxa, Praetors' Voice"),
        listOf(seat("AI 2", "Edgar Markov"), seat("AI 3", "The Ur-Dragon"), seat("AI 4", "Krenko, Mob Boss")),
        3,
    ).also {
        it.setKind(1, ArgentumWaitingLobby.Kind.FRIEND)
        it.setKind(2, ArgentumWaitingLobby.Kind.FRIEND)
    }

    @Test fun rosterSeatsCarryKindCommanderAndReadiness() {
        val lobby = room()
        val ana = lobby.seatGuest(seat("Ana", "Light-Paws, Emperor's Voice"))!!
        val seats = lobbySeats(lobby.lobbyMessage())
        assertEquals(listOf("host", "human", "waiting"), seats.map(LobbySeat::kind))
        assertEquals("Light-Paws, Emperor's Voice", seats[1].commander)
        assertEquals(ana.value, seats[1].seatId)
        assertTrue(seats[0].ready)
        assertFalse(seats[1].ready)
        assertNull(seats[2].commander)
        assertEquals("Waiting for a friend", seats[2].name)
    }

    @Test fun statusLineSaysWhatTheTableIsWaitingFor() {
        val lobby = room()
        assertEquals("Waiting for 2 more friends to join.", lobbyStatusLine(lobby.lobbyMessage(), isHost = true))
        val ana = lobby.seatGuest(seat("Ana", "Light-Paws, Emperor's Voice"))!!
        assertEquals("Waiting for 1 more friend to join.", lobbyStatusLine(lobby.lobbyMessage(), isHost = true))
        val ben = lobby.seatGuest(seat("Ben", "Edgar Markov"))!!
        assertEquals("Waiting for Ana, Ben to ready up.", lobbyStatusLine(lobby.lobbyMessage(), isHost = false))
        lobby.setReady(ana, true)
        lobby.setReady(ben, true)
        assertEquals("Everyone is ready. Start the game when you're set.", lobbyStatusLine(lobby.lobbyMessage(), isHost = true))
        assertEquals("Everyone is ready. Waiting for the host to start.", lobbyStatusLine(lobby.lobbyMessage(), isHost = false))
        assertEquals("Connected. Waiting for the host…", lobbyStatusLine(null, isHost = false))
    }

    @Test fun emptyOrMalformedRosterIsEmpty() {
        assertEquals(emptyList<LobbySeat>(), lobbySeats(null))
        assertEquals(emptyList<LobbySeat>(), lobbySeats(org.json.JSONObject().put("type", "lanLobby")))
    }
}
