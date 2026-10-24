package com.arcanatable

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/**
 * Android's per-seat masked state contract for The Lunar Whale. These tests deliberately use
 * the wire shape from Argentum ClientStateTransformer: a partly known library has cardIds AND
 * positions. Looking requires no priority, while playing/casting always requires legalActions.
 */
class LunarWhaleTopLibraryTest {
    private fun library(owner: String, vararg known: Pair<String, Int>): JSONObject =
        JSONObject()
            .put("zoneId", JSONObject().put("zoneType", "Library").put("ownerId", owner))
            .put("size", 12)
            .put("cardIds", JSONArray(known.map { it.first }))
            .put("positions", JSONArray(known.map { it.second }))

    private fun state(viewer: String, first: JSONObject, second: JSONObject = library("p2")): JSONObject =
        JSONObject()
            .put("viewingPlayerId", viewer)
            .put("activePlayerId", "p2")
            .put("priorityPlayerId", "p2") // the inspecting player explicitly has NO priority
            .put("currentPhase", "MAIN")
            .put("currentStep", "PRECOMBAT_MAIN")
            .put("players", JSONArray()
                .put(JSONObject().put("playerId", "p1").put("name", "You").put("librarySize", 12))
                .put(JSONObject().put("playerId", "p2").put("name", "Opponent").put("librarySize", 12)))
            .put("cards", JSONObject()
                .put("forest", JSONObject().put("name", "Forest").put("typeLine", "Basic Land — Forest")
                    .put("zone", JSONObject().put("zoneType", "Library").put("ownerId", "p1")))
                .put("frogmite", JSONObject().put("name", "Frogmite").put("typeLine", "Artifact Creature — Frog")
                    .put("manaCost", "{4}").put("zone", JSONObject().put("zoneType", "Library").put("ownerId", "p1"))))
            .put("zones", JSONArray().put(first).put(second))

    @Test fun privatelyKnownTopIsVisibleWithoutPriorityAndLibraryRemainsMasked() {
        val snapshot = ArgentumTableSnapshot.parse(state("p1", library("p1", "forest" to 0)))
        assertEquals("Forest", snapshot.topLibraryCard?.name)
        assertEquals(1, snapshot.priorityPlayer)
        assertTrue("all remaining library cards remain hidden", snapshot.players[0].library.all { it.name == "Hidden card" })
        assertEquals(12, snapshot.players[0].library.size)
        assertEquals("forest", snapshot.serverCardIds[snapshot.topLibraryCard!!.id])
    }

    @Test fun partiallyKnownLibraryCardAtNonzeroPositionIsNotTop() {
        val snapshot = ArgentumTableSnapshot.parse(state("p1", library("p1", "frogmite" to 4)))
        assertNull(snapshot.topLibraryCard)
    }

    @Test fun multipleKnownPositionsSelectOnlyPositionZero() {
        val snapshot = ArgentumTableSnapshot.parse(state("p1", library("p1", "frogmite" to 4, "forest" to 0)))
        assertEquals("Forest", snapshot.topLibraryCard?.name)
    }

    @Test fun absenceOfExplicitPositionNeverGuessesAtTop() {
        val zone = JSONObject().put("zoneId", JSONObject().put("zoneType", "Library").put("ownerId", "p1"))
            .put("size", 12).put("cardIds", JSONArray().put("forest"))
        assertNull(ArgentumTableSnapshot.parse(state("p1", zone)).topLibraryCard)
    }

    @Test fun opponentsLibraryCardsAreNeverPromotedToPrivateViewerTop() {
        // Even an incorrectly over-detailed inbound zone must not be presented as the viewer's top.
        val snapshot = ArgentumTableSnapshot.parse(state("p2", library("p1", "forest" to 0)))
        assertNull(snapshot.topLibraryCard)
    }

    @Test fun lookDoesNotCreatePlayPermissionAndOnlyServerActionsCanEnableIt() {
        val state = state("p1", library("p1", "frogmite" to 0))
        val without = ArgentumTableSnapshot.parse(state)
        val top = without.topLibraryCard!!
        assertTrue(ArgentumActions.forCardServerId(without.legalActions, without.serverCardIds[top.id]).isEmpty())

        val allowed = JSONArray().put(JSONObject()
            .put("action", JSONObject().put("type", "CastSpell").put("cardId", "frogmite"))
            .put("isAffordable", true))
        val with = ArgentumTableSnapshot.parse(state, allowed)
        assertEquals(listOf("CastSpell"), ArgentumActions.forCardServerId(
            with.legalActions, with.serverCardIds[with.topLibraryCard!!.id]
        ).map { it.optJSONObject("action").optString("type") })
    }

    @Test fun topChangesReplaceTheLookAndNeverRetainStaleCard() {
        val before = ArgentumTableSnapshot.parse(state("p1", library("p1", "forest" to 0)))
        val after = ArgentumTableSnapshot.parse(state("p1", library("p1", "frogmite" to 0)))
        val hidden = ArgentumTableSnapshot.parse(state("p1", library("p1")))
        assertEquals("Forest", before.topLibraryCard?.name)
        assertEquals("Frogmite", after.topLibraryCard?.name)
        assertNull(hidden.topLibraryCard)
    }
}
