package com.arcanatable

import com.arcanatable.engine.GameOutcomeDetails
import com.wingedsheep.engine.core.GameEndReason
import com.wingedsheep.engine.core.GameEndedEvent
import com.wingedsheep.engine.core.PlayerLostEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.engine.state.components.player.LossReason
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

    @Test fun nullableViewerNeverClaimsAnUnidentifiedViewerWon() {
        val packet = JSONObject().put("winnerId", "offline-you")
            .put("winnerName", "Tails").put("reason", "OPPONENT_LOST")
        val unknownViewer = ArgentumOutcomeLog.banner(packet, null, emptyMap())
        assertTrue(unknownViewer.contains("Tails won."))
        assertFalse(unknownViewer.contains("You won."))
        assertTrue(ArgentumOutcomeLog.banner(packet, "offline-you", emptyMap()).contains("You won."))
        assertTrue(ArgentumOutcomeLog.banner(packet, "offline-ai", emptyMap()).contains("Tails won."))

        val noWinner = JSONObject().put("reason", "DRAW")
        assertTrue(ArgentumOutcomeLog.banner(noWinner, null, emptyMap()).contains("No winner."))
    }

    @Test fun lastHumanEliminatedFromFourPlayerPodDoesNotDeclareWinnerOrDraw() {
        val ai2 = EntityId("offline-ai-2")
        val ai3 = EntityId("offline-ai-3")
        val state = GameState(
            turnOrder = listOf(winner, loser, ai2, ai3),
            entities = mapOf(winner to ComponentContainer.of(PlayerLostComponent(LossReason.LIFE_ZERO))),
            winnerId = null,
            gameOver = false,
        )
        val packet = GameOutcomeDetails.toHumanEliminatedMessage(
            state, winner, names,
            listOf(PlayerLostEvent(winner, GameEndReason.LIFE_ZERO, "Combat damage reduced life to 0.")),
        )
        assertEquals("gameOver", packet.getString("type"))
        assertEquals("YOU_LOST", packet.getString("reason"))
        assertTrue(packet.getBoolean("matchUnfinished"))
        assertEquals(3, packet.getInt("activePlayersRemaining"))
        assertTrue(packet.isNull("winnerId"))
        assertEquals("offline-you", packet.getString("eliminatedPlayerId"))
        assertEquals("LIFE_ZERO", packet.getString("engineReason"))
        assertEquals("Combat damage reduced life to 0.", packet.getString("winCondition"))
        assertEquals("LIFE_ZERO", packet.getJSONArray("losers").getJSONObject(0).getString("reason"))
        assertTrue(packet.getString("message").contains("3 other player(s) were still active"))
        assertTrue(ArgentumOutcomeLog.diagnostic(packet, emptyMap()).contains("match unfinished"))
        assertTrue(ArgentumOutcomeLog.banner(packet, "offline-you", emptyMap()).contains("You were eliminated"))
        assertFalse(ArgentumOutcomeLog.banner(packet, "offline-you", emptyMap()).contains("No winner."))
    }

    @Test fun unknownEliminationReasonIsMarkedUnknownRatherThanBlamingAnotherPlayer() {
        val ai2 = EntityId("offline-ai-2")
        // Deliberately unavailable viewer state: a legacy/partial terminal notice must not
        // borrow another player's card-effect reason.
        val state = GameState(
            turnOrder = listOf(loser, ai2),
            entities = mapOf(ai2 to ComponentContainer.of(PlayerLostComponent(LossReason.CARD_EFFECT))),
            winnerId = null,
            gameOver = false,
        )
        val previous = PlayerLostEvent(ai2, GameEndReason.CARD_EFFECT, "Old card effect.")
        val packet = GameOutcomeDetails.toHumanEliminatedMessage(state, winner, names, listOf(previous))
        assertEquals("UNKNOWN", packet.getString("engineReason"))
        assertTrue(packet.isNull("winCondition"))
        assertFalse(packet.getString("message").contains("Old card effect"))
        assertTrue(packet.getBoolean("matchUnfinished"))
    }

    @Test fun legacyOnlinePacketStillProvidesAnEndMessage() {
        val packet = JSONObject().put("winnerId", "seat-2").put("reason", "YOU_LOST")
        assertTrue(ArgentumOutcomeLog.banner(packet, "seat-1", mapOf("seat-2" to "Opponent")).contains("Opponent won."))
        assertTrue(ArgentumOutcomeLog.diagnostic(packet, mapOf("seat-2" to "Opponent")).contains("winner=Opponent"))
    }
}
