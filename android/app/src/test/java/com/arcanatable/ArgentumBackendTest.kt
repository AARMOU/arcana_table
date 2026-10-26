package com.arcanatable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test

class ArgentumBackendTest {
    @Test
    fun commanderIsRemovedFromLibraryPayloadAndSentSeparately() {
        val importedNames = listOf(" Avatar Aang ") + (1..99).map { "Card $it" }

        val library = argentumCommanderLibraryDeck(importedNames, "Avatar Aang")

        assertEquals(99, library.values.sum())
        assertFalse(library.keys.any { it.equals("Avatar Aang", ignoreCase = true) })
    }

    @Test
    fun commanderRemovalDoesNotDropAnAdditionalCopy() {
        val library = argentumCommanderLibraryDeck(listOf("Avatar Aang", "Avatar Aang", "Plains"), "Avatar Aang")

        assertEquals(mapOf("Avatar Aang" to 1, "Plains" to 1), library)
    }

    @Test
    fun validationSeparatesUnknownCardsFromOtherFormatErrors() {
        val result = ArgentumDeckValidation.parse(
            """{"valid":false,"totalCards":98,"errors":[
                {"code":"UNKNOWN_CARD","message":"Unknown card: \"Uchuulon\"","cardName":"Uchuulon"},
                {"code":"INVALID_SIZE","message":"Commander decks need 100 cards.","cardName":null}],
                "warnings":[]}"""
        )

        assertFalse(result.valid)
        assertEquals(98, result.totalCards)
        assertEquals(setOf("Uchuulon"), result.unsupportedCards)
        assertEquals("INVALID_SIZE", result.errors.last().code)
    }

    @Test
    fun validServerResponseHasNoUnsupportedCards() {
        val result = ArgentumDeckValidation.parse(
            """{"valid":true,"totalCards":100,"errors":[],"warnings":[]}"""
        )

        assertTrue(result.valid)
        assertTrue(result.unsupportedCards.isEmpty())
    }

    @Test
    fun tableProjectionKeepsServerMaskingAndMapsOtherBoards() {
        val state = JSONObject().apply {
            put("viewingPlayerId", "p1")
            put("activePlayerId", "p2")
            put("turnNumber", 3)
            put("currentPhase", "MAIN")
            put("currentStep", "PRECOMBAT_MAIN")
            put("isGameOver", false)
            put("players", JSONArray().put(JSONObject().put("playerId", "p1").put("name", "You").put("life", 40).put("handSize", 1).put("librarySize", 1))
                .put(JSONObject().put("playerId", "p2").put("name", "AI").put("life", 28).put("handSize", 1).put("librarySize", 2)))
            put("cards", JSONObject()
                .put("hand-1", JSONObject().put("id", "hand-1").put("name", "Plains").put("typeLine", "Basic Land — Plains").put("controllerId", "p1").put("ownerId", "p1").put("isTapped", false).put("zone", JSONObject().put("zoneType", "HAND").put("ownerId", "p1")))
                .put("board-1", JSONObject().put("id", "board-1").put("name", "Sol Ring").put("typeLine", "Artifact").put("controllerId", "p1").put("ownerId", "p1").put("isTapped", false).put("zone", JSONObject().put("zoneType", "BATTLEFIELD").put("ownerId", "p1"))))
            put("zones", JSONArray()
                .put(JSONObject().put("zoneId", JSONObject().put("zoneType", "HAND").put("ownerId", "p1")).put("cardIds", JSONArray().put("hand-1")).put("size", 1))
                .put(JSONObject().put("zoneId", JSONObject().put("zoneType", "LIBRARY").put("ownerId", "p1")).put("cardIds", JSONArray()).put("size", 1))
                .put(JSONObject().put("zoneId", JSONObject().put("zoneType", "HAND").put("ownerId", "p2")).put("cardIds", JSONArray()).put("size", 1))
                .put(JSONObject().put("zoneId", JSONObject().put("zoneType", "LIBRARY").put("ownerId", "p2")).put("cardIds", JSONArray()).put("size", 2))
                .put(JSONObject().put("zoneId", JSONObject().put("zoneType", "BATTLEFIELD").put("ownerId", "p1")).put("cardIds", JSONArray().put("board-1")).put("size", 1)))
        }

        val projected = ArgentumTableSnapshot.parse(state, interactionEpoch = "epoch-4")

        assertEquals(0, projected.viewingPlayer)
        assertEquals(1, projected.activePlayer)
        assertEquals(3, projected.turnNumber)
        assertEquals("epoch-4", projected.interactionEpoch)
        assertEquals(1, projected.players[0].hand.size)
        assertEquals("Plains", projected.players[0].hand.single().name)
        assertEquals("Hidden card", projected.players[1].hand.single().name)
        assertEquals(2, projected.players[1].library.size)
        assertEquals("Sol Ring", projected.players[0].battlefield.single().name)
    }
    @Test fun commanderDamageProjectionAndTrackerUseCommanderNameNotSeatId() {
        val state = JSONObject().apply {
            put("viewingPlayerId", "p1")
            put("activePlayerId", "p2")
            put("players", JSONArray()
                .put(JSONObject().put("playerId", "p1").put("name", "You").put("life", 8)
                    .put("commanderDamage", JSONArray().put(JSONObject()
                        .put("commanderName", "Animar, Soul of Elements").put("commanderId", "cmd-2").put("amount", 20))))
                .put(JSONObject().put("playerId", "p2").put("name", "AI · Animar").put("life", 41)))
            put("cards", JSONObject().put("cmd-2", JSONObject()
                .put("id", "cmd-2").put("name", "Animar, Soul of Elements")
                .put("typeLine", "Legendary Creature — Elemental")
                .put("ownerId", "p2").put("controllerId", "p2").put("isCommander", true)))
            put("zones", JSONArray().put(JSONObject()
                .put("zoneId", JSONObject().put("zoneType", "COMMAND").put("ownerId", "p2"))
                .put("cardIds", JSONArray().put("cmd-2")).put("size", 1)))
        }
        val projected = ArgentumTableSnapshot.parse(state)
        assertEquals("Animar, Soul of Elements", projected.players[1].commanderName)
        val rows = commanderDamageTrackerRows(projected.players[0].commanderDamageReceived,
            listOf(CommanderDamageTrackerSource(projected.players[1].id, projected.players[1].name,
                projected.players[1].commanderName)))
        assertEquals(1, rows.size)
        assertEquals("Animar, Soul of Elements", rows.single().key)
        assertEquals(20, rows.single().amount)
    }

    @Test fun commanderDamageTrackerPreservesLegacySeatAndSeparatePartnerCommanderCounts() {
        val sources = listOf(CommanderDamageTrackerSource("p2", "Opponent", "First Commander"))
        val manual = commanderDamageTrackerRows(mapOf("p2" to 7), sources)
        assertEquals("p2", manual.single().key)
        assertEquals(7, manual.single().amount)

        val engine = commanderDamageTrackerRows(mapOf("First Commander" to 13, "Partner Commander" to 9), sources)
        assertEquals(2, engine.size)
        assertEquals("First Commander", engine[0].key)
        assertEquals(13, engine[0].amount)
        assertEquals("Partner Commander", engine[1].key)
        assertEquals(9, engine[1].amount)
        assertTrue(engine.none { it.amount >= 21 })
    }

    @Test fun commanderDamageTrackerDoesNotHideUnknownEngineSource() {
        val rows = commanderDamageTrackerRows(mapOf("Unknown Commander" to 21),
            listOf(CommanderDamageTrackerSource("p2", "Opponent", null)))
        assertEquals(2, rows.size)
        assertEquals(0, rows[0].amount)
        assertEquals("Unknown Commander", rows[1].key)
        assertEquals(21, rows[1].amount)
    }

}
