package com.arcanatable

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArgentumPassingTest {
    @Test
    fun passLabelNamesWhereArgentumWillStop() {
        assertEquals("PASS → TO COMBAT", ArgentumPassing.passLabel("To Combat"))
        assertEquals("PASS → RESOLVE", ArgentumPassing.passLabel("Resolve"))
        assertEquals("PASS", ArgentumPassing.passLabel("Pass"))
        assertEquals("PASS PRIORITY", ArgentumPassing.passLabel(null))
        assertEquals("PASS PRIORITY", ArgentumPassing.passLabel(" "))
    }

    private fun tick(
        turn: Int = 3, active: Int = 1, priority: Int = 0,
        types: Set<String> = setOf("PassPriority"), decision: Boolean = false,
        opponentStack: String? = null, awaiting: Boolean = false, over: Boolean = false,
    ) = ArgentumPassing.next(
        startTurn = 2, turnNumber = turn, activePlayer = active, localSeat = 0, priorityPlayer = priority,
        legalActionTypes = types, hasPendingDecision = decision, opponentStackItem = opponentStack,
        gameOver = over, awaitingUpdate = awaiting,
    ).step

    @Test
    fun passesThroughTheOpponentsTurn() {
        assertEquals(ArgentumPassing.Step.PASS, tick())
        assertEquals(ArgentumPassing.Step.WAIT, tick(awaiting = true))
        assertEquals(ArgentumPassing.Step.WAIT, tick(priority = 1, types = emptySet()))
    }

    @Test
    fun keepsPassingTheRestOfTheTurnItStartedOn() {
        assertEquals(ArgentumPassing.Step.PASS, tick(turn = 2, active = 0))
    }

    @Test
    fun stopsWhenYourNextTurnStarts() {
        assertEquals(ArgentumPassing.Step.REACHED_YOUR_TURN, tick(turn = 4, active = 0))
    }

    @Test
    fun stopsForBlocksSpellsAndDecisions() {
        assertEquals(ArgentumPassing.Step.INTERRUPTED, tick(types = setOf("DeclareBlockers")))
        assertEquals(ArgentumPassing.Step.INTERRUPTED, tick(opponentStack = "Lightning Bolt"))
        assertEquals(ArgentumPassing.Step.INTERRUPTED, tick(decision = true))
        assertEquals(ArgentumPassing.Step.INTERRUPTED, tick(over = true))
    }

    @Test
    fun gameLogParsesWholeGameWithOwnershipAndTurnMarkers() {
        val log = JSONArray()
            .put(JSONObject().put("type", "turnChanged").put("turnNumber", 1).put("activePlayerId", "me").put("isYourTurn", true).put("description", "--- Turn 1 (Your turn) ---"))
            .put(JSONObject().put("type", "spellCast").put("casterId", "me").put("description", "You cast Llanowar Elves"))
            .put(JSONObject().put("type", "lifeChanged").put("playerId", "them").put("isYours", false).put("description", "Opponent lost 2 life"))
            .put(JSONObject().put("type", "permanentEntered").put("controllerId", "them").put("description", "Goblin entered"))
            .put(JSONObject().put("type", "noOwner").put("description", "Something happened"))
            .put(JSONObject().put("type", "blank").put("description", ""))
        val lines = ArgentumLogLine.parseAll(log, viewerId = "me")
        assertEquals(5, lines.size)
        assertTrue(lines[0].isTurnMarker)
        assertEquals(true, lines[1].isYours)
        assertEquals(false, lines[2].isYours)
        assertEquals(false, lines[3].isYours)
        assertEquals(null, lines[4].isYours)
        assertFalse(lines[1].isTurnMarker)
    }

    @Test
    fun snapshotKeepsTheFullLogAndTheRecentFeed() {
        val log = JSONArray()
        repeat(30) { log.put(JSONObject().put("type", "x").put("description", "event $it")) }
        val state = JSONObject().put("viewingPlayerId", "me").put("players", JSONArray()).put("gameLog", log)
        val snapshot = ArgentumTableSnapshot.parse(state)
        assertEquals(30, snapshot.fullLog.size)
        assertEquals("event 0", snapshot.fullLog.first().description)
        assertEquals(12, snapshot.recentLog.size)
        assertEquals("event 29", snapshot.recentLog.first().description)
    }
}
