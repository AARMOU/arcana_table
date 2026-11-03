package com.arcanatable

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Phones on different Arcana builds (different rules engines) are told so, in plain words. */
@RunWith(RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [34])
class BuildCompatibilityTest {
    private val me = BuildConfig.VERSION_CODE
    private fun packet(build: Int?) = JSONObject().put("type", "lanHello").also {
        if (build != null) it.put(BuildCompatibility.FIELD, build)
    }

    @Test fun sameBuildPlaysTogether() {
        assertNull(BuildCompatibility.guestProblem(packet(me)))
        assertNull(BuildCompatibility.hostProblem(packet(me)))
    }

    @Test fun differentBuildIsRefusedWithBothBuildNumbers() {
        val guest = BuildCompatibility.guestProblem(packet(me + 1))
        assertNotNull(guest)
        assertTrue(guest!!, guest.contains("Build ${me + 1}") && guest.contains("Build $me") && guest.contains("same Arcana build"))
        val host = BuildCompatibility.hostProblem(packet(me - 1))
        assertTrue(host!!, host.contains("host's Arcana is Build ${me - 1}") && host.contains("yours is Build $me"))
    }

    @Test fun olderBuildsSendNoNumberAndCountAsOlder() {
        assertTrue(BuildCompatibility.guestProblem(packet(null))!!.contains("an older build"))
        assertTrue(BuildCompatibility.hostProblem(packet(null))!!.contains("an older build"))
    }

    @Test fun waitingLobbyAndHelloCarryTheBuild() {
        val seat = { name: String -> com.arcanatable.engine.LocalSeat(name, List(40) { "Forest" }, null) }
        val lobby = ArgentumWaitingLobby(seat("YOU"), listOf(seat("AI")), 2).lobbyMessage()
        assertEquals(me, lobby.optInt(BuildCompatibility.FIELD))
        assertNull(BuildCompatibility.hostProblem(lobby))
    }

    @Test fun savedGameFromAnotherBuildSaysToStartANewGame() {
        val text = BuildCompatibility.savedGameProblem(JSONObject().put(BuildCompatibility.FIELD, me - 1))
        assertTrue(text, text.contains("Build ${me - 1}") && text.contains("Start a new game"))
        assertTrue(BuildCompatibility.savedGameProblem(JSONObject()).contains("an older build"))
    }
}
