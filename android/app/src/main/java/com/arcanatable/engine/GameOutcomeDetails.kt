package com.arcanatable.engine

import com.wingedsheep.engine.core.GameEndReason
import com.wingedsheep.engine.core.GameEndedEvent
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.PlayerLostEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.sdk.model.EntityId
import org.json.JSONArray
import org.json.JSONObject

/**
 * Evidence for a finished offline game, taken from the authoritative result events and final
 * battlefield. Never infer a winning card from the last spell/decision or from a UI recap.
 *
 * Keep the legacy, viewer-relative `reason` field for existing clients. `engineReason`,
 * `winCondition` and `losers` describe what actually ended the game.
 */
internal object GameOutcomeDetails {
    fun toMessage(
        state: GameState,
        viewer: EntityId,
        nameFor: (EntityId) -> String,
        events: List<GameEvent>,
    ): JSONObject {
        val winner = state.winnerId
        val lossEvents = events.filterIsInstance<PlayerLostEvent>()
        val ended = events.filterIsInstance<GameEndedEvent>().lastOrNull()
        // The latest elimination controls causality. An unrelated card effect that removed an
        // earlier player must not be advertised as the cause of a later life-total loss.
        val latestLoss = lossEvents.lastOrNull()
        val detailedLoss = latestLoss?.takeIf {
            !it.message.isNullOrBlank() && (ended == null || it.reason == ended.reason)
        }
        val engineReason = ended?.reason ?: latestLoss?.reason
        val winCondition = detailedLoss?.message?.takeIf(String::isNotBlank)
        val sourceCard = winCondition?.substringBefore(':')?.takeIf {
            it != winCondition && it.isNotBlank() && it.length <= 100
        }

        // Only report an artifact group for the specific condition described by the engine.
        // This is corroborating public battlefield evidence, not the source of the win decision.
        val artifactGroup = if (winner != null &&
            winCondition?.contains("eight or more artifacts with the same name", ignoreCase = true) == true
        ) {
            state.getBattlefield().mapNotNull { id ->
                val entity = state.getEntity(id) ?: return@mapNotNull null
                val card = entity.get<CardComponent>() ?: return@mapNotNull null
                if (entity.get<ControllerComponent>()?.playerId == winner && card.typeLine.isArtifact) card.name else null
            }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.takeIf { it.value >= 8 }
        } else null

        val lossByPlayer = lossEvents.associateBy { it.playerId }
        val eliminated = (lossEvents.map { it.playerId } +
            state.turnOrder.filter { it != winner && it !in state.activePlayers }).distinct()
        val losers = JSONArray()
        eliminated.forEach { id ->
            val loss = lossByPlayer[id]
            val storedReason = state.getEntity(id)?.get<PlayerLostComponent>()?.reason?.name
            val item = JSONObject()
                .put("playerId", id.value)
                .put("playerName", nameFor(id))
                .put("reason", loss?.reason?.name ?: storedReason ?: "UNKNOWN")
            loss?.message?.takeIf(String::isNotBlank)?.let { item.put("detail", it) }
            if (loss?.reason == GameEndReason.LIFE_ZERO) {
                state.getEntity(id)?.get<LifeTotalComponent>()?.life?.let { item.put("life", it) }
            }
            losers.put(item)
        }

        return JSONObject()
            .put("type", "gameOver")
            .put("winnerId", winner?.value ?: JSONObject.NULL)
            .put("winnerName", winner?.let(nameFor) ?: JSONObject.NULL)
            .put("reason", if (winner == viewer) "OPPONENT_LOST" else if (winner == null) "DRAW" else "YOU_LOST")
            .put("engineReason", engineReason?.name ?: "UNKNOWN")
            .put("winCondition", winCondition ?: JSONObject.NULL)
            .put("sourceCard", sourceCard ?: JSONObject.NULL)
            .put("artifactName", artifactGroup?.key ?: JSONObject.NULL)
            .put("artifactCount", artifactGroup?.value ?: JSONObject.NULL)
            .put("losers", losers)
    }
}
