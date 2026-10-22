package com.arcanatable

import com.arcanatable.engine.GameOutcomeDetails
import com.wingedsheep.engine.core.GameEndReason
import com.wingedsheep.engine.core.GameEndedEvent
import com.wingedsheep.engine.core.PlayerLostEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameOutcomeDetailsTest {
    private val winner = EntityId("offline-you")
    private val loser = EntityId("offline-ai")
    private val names: (EntityId) -> String = { if (it == winner) "Tails" else "Animar" }

    @Test fun alternativeWinCarriesAuthoritativeCardAndThresholdNotAnInferredCard() {
        val state = GameState(turnOrder = listOf(winner, loser), winnerId = winner, gameOver = true)
        val engineMessage = "Mechanized Production: controlled eight or more artifacts with the same name."
        val events = listOf(
            PlayerLostEvent(loser, GameEndReason.CARD_EFFECT, engineMessage),
            GameEndedEvent(winner, GameEndReason.CARD_EFFECT)
        )
        val packet = GameOutcomeDetails.toMessage(state, winner, names, events) { it == "Mechanized Production" }
        assertEquals("OPPONENT_LOST", packet.getString("reason"))
        assertEquals("CARD_EFFECT", packet.getString("engineReason"))
        assertEquals("offline-you", packet.getString("winnerId"))
        assertEquals("Tails", packet.getString("winnerName"))
        assertEquals("Mechanized Production", packet.getString("sourceCard"))
        assertEquals(engineMessage, packet.getString("winCondition"))
        assertTrue("No group count without battlefield evidence", packet.isNull("artifactCount"))
        val loss = packet.getJSONArray("losers").getJSONObject(0)
        assertEquals("Animar", loss.getString("playerName"))
        assertEquals("CARD_EFFECT", loss.getString("reason"))
        assertEquals(engineMessage, loss.getString("detail"))
        assertEquals("YOU_LOST", GameOutcomeDetails.toMessage(state, loser, names, events).getString("reason"))
    }

    @Test fun ordinaryDeathAndUnexplainedCardEffectAreNotMisidentifiedAsAnAlternateWin() {
        val state = GameState(turnOrder = listOf(winner, loser), winnerId = winner, gameOver = true)
        val events = listOf(PlayerLostEvent(loser, GameEndReason.LIFE_ZERO),
            GameEndedEvent(winner, GameEndReason.LIFE_ZERO))
        val ordinary = GameOutcomeDetails.toMessage(state, winner, names, events)
        assertEquals("LIFE_ZERO", ordinary.getString("engineReason"))
        assertTrue(ordinary.isNull("winCondition"))
        assertTrue(ordinary.isNull("sourceCard"))
        val unknownSource = GameOutcomeDetails.toMessage(
            state, winner, names,
            listOf(PlayerLostEvent(loser, GameEndReason.CARD_EFFECT),
                GameEndedEvent(winner, GameEndReason.CARD_EFFECT))
        )
        assertEquals("CARD_EFFECT", unknownSource.getString("engineReason"))
        assertTrue(unknownSource.isNull("winCondition"))
        assertTrue(unknownSource.isNull("sourceCard"))
    }

    @Test fun earlierMultiplayerCardEffectIsNotMisreportedAsTheFinalWin() {
        val previousLoser = EntityId("offline-ai-2")
        val state = GameState(
            turnOrder = listOf(winner, previousLoser, loser),
            winnerId = winner,
            gameOver = true
        )
        val events = listOf(
            PlayerLostEvent(previousLoser, GameEndReason.CARD_EFFECT, "An earlier card's effect."),
            PlayerLostEvent(loser, GameEndReason.LIFE_ZERO),
            GameEndedEvent(winner, GameEndReason.LIFE_ZERO)
        )
        val packet = GameOutcomeDetails.toMessage(state, winner, names, events)
        assertEquals("LIFE_ZERO", packet.getString("engineReason"))
        assertTrue(packet.isNull("winCondition"))
        assertTrue(packet.isNull("sourceCard"))
        assertEquals(2, packet.getJSONArray("losers").length())
    }

    @Test fun drawnGameHasNoInventedWinner() {
        val state = GameState(turnOrder = listOf(winner, loser), winnerId = null, gameOver = true)
        val packet = GameOutcomeDetails.toMessage(state, winner, names,
            listOf(GameEndedEvent(null, GameEndReason.DRAW)))
        assertEquals("DRAW", packet.getString("reason"))
        assertEquals("DRAW", packet.getString("engineReason"))
        assertTrue(packet.isNull("winnerId"))
    }

    @Test fun diagnosticAndBannerPreservePreciseWinEvidence() {
        val packet = JSONObject()
            .put("winnerId", "offline-you").put("winnerName", "Tails")
            .put("reason", "OPPONENT_LOST").put("engineReason", "CARD_EFFECT")
            .put("sourceCard", "Mechanized Production")
            .put("winCondition", "Mechanized Production: controlled eight or more artifacts with the same name.")
            .put("artifactName", "Sol Ring").put("artifactCount", 8)
            .put("losers", JSONArray().put(JSONObject().put("playerId", "offline-ai")
                .put("playerName", "Animar").put("reason", "CARD_EFFECT")))
        val text = ArgentumOutcomeLog.diagnostic(packet, emptyMap())
        assertTrue(text.contains("winner=Tails"))
        assertTrue(text.contains("losers=Animar (CARD_EFFECT)"))
        assertTrue(text.contains("source=Mechanized Production"))
        assertTrue(text.contains("verifiedArtifactGroup=Sol Ring x8"))
        assertTrue(ArgentumOutcomeLog.banner(packet, "offline-you", emptyMap()).contains("You won."))
        assertTrue(ArgentumOutcomeLog.banner(packet, "offline-ai", emptyMap()).contains("Tails won."))
        assertFalse(ArgentumOutcomeLog.banner(packet, "offline-you", emptyMap()).contains("Reason: opponent lost"))
    }

    @Test fun legacyOnlinePacketStillProvidesAnEndMessage() {
        val packet = JSONObject().put("winnerId", "seat-2").put("reason", "YOU_LOST")
        assertTrue(ArgentumOutcomeLog.banner(packet, "seat-1", mapOf("seat-2" to "Opponent")).contains("Opponent won."))
        assertTrue(ArgentumOutcomeLog.diagnostic(packet, mapOf("seat-2" to "Opponent")).contains("winner=Opponent"))
    }
}
