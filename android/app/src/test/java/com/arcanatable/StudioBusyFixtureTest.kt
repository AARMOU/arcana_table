package com.arcanatable

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Acceptance of the J10 data through the real app projection, not merely valid JSON. */
class StudioBusyFixtureTest {
    private fun fixture() = JSONObject(requireNotNull(javaClass.classLoader!!.getResource(
        "argentum/studio-busy-four-player.json"
    )).readText())

    @Test fun crowdedStateSurvivesAppProjection() {
        val message = fixture()
        val state = message.getJSONObject("state")
        val snapshot = ArgentumTableSnapshot.parse(state, message.getJSONArray("legalActions"))
        assertEquals(4, snapshot.players.size)
        assertEquals(listOf(108, 143, -9, 126), snapshot.players.map { it.life })
        val you = snapshot.players[snapshot.viewingPlayer]
        assertEquals(state.getString("viewingPlayerId"), you.id)
        assertEquals(18, you.battlefield.size)
        assertEquals(3, you.battlefield.count { it.isToken })
        assertEquals(58, snapshot.players.sumOf { it.graveyard.size })
        assertEquals(6, snapshot.stack.size)
        assertEquals(6, snapshot.stack.map { it.id }.distinct().size)
        assertEquals(mapOf("e100" to 18), you.commanderDamageReceived)
        assertEquals(mapOf("e0" to 19), snapshot.players.single { it.id == "studio-seat-3" }.commanderDamageReceived)
        assertTrue(snapshot.players.flatMap { it.commanderDamageReceived.keys }.all { it.isNotBlank() })
        assertTrue("Synthetic layout fixture must not offer fake game actions", snapshot.legalActions.isEmpty())
    }

    @Test fun zonesAndAttachmentReferencesAreComplete() {
        val state = fixture().getJSONObject("state")
        val cards = state.getJSONObject("cards")
        val zones = state.getJSONArray("zones")
        val seen = mutableSetOf<String>()
        for (i in 0 until zones.length()) {
            val zone = zones.getJSONObject(i)
            val ids = zone.getJSONArray("cardIds")
            assertEquals(zone.getInt("size"), ids.length())
            for (n in 0 until ids.length()) {
                val id = ids.getString(n)
                assertTrue("Duplicate zone membership: $id", seen.add(id))
                assertTrue("Missing card: $id", cards.has(id))
            }
        }
        val attached = cards.keys().asSequence().map { cards.getJSONObject(it) }
            .filter { !it.isNull("attachedTo") && it.optString("attachedTo").isNotBlank() }.toList()
        assertEquals(2, attached.size)
        attached.forEach { assertTrue(cards.has(it.getString("attachedTo"))) }
    }
}
