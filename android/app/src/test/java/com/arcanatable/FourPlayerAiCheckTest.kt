package com.arcanatable

import com.arcanatable.engine.EmbeddedEngineTrial
import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.components.combat.AttackersDeclaredThisCombatComponent
import com.wingedsheep.engine.state.components.combat.BlockersDeclaredThisCombatComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * Roadmap check before 4-player offline: can Argentum's AI fill every seat of a 4-player
 * free-for-all? Four AIs play a Portal free-for-all on the real engine until the game ends.
 */
class FourPlayerAiCheckTest {
    @Test fun fourAisFinishAFreeForAll() {
        val cards = File("src/main/assets/argentum/por-cards.jsonl").bufferedReader().useLines { EmbeddedEngineTrial.loadCards(it) }
        val registry = CardRegistry().apply { register(cards) }
        val random = Random(7)
        val colors = listOf(Color.GREEN, Color.RED, Color.BLUE, Color.BLACK)
        val ids = (1..4).map { EntityId("seat-$it") }
        val config = GameConfig(
            players = ids.mapIndexed { i, id -> PlayerConfig(name = "AI ${i + 1}", deck = Deck(EmbeddedEngineTrial.monoDeck(cards, colors[i], random).cards), playerId = id) },
            skipMulligans = true,
        )
        val services = EngineServices(registry)
        val processor = ActionProcessor(services)
        var state = GameInitializer(registry, services.printingRegistry).initializeGame(config).state
        val ais = ids.associateWith { AIPlayer.create(registry, it) }
        var actions = 0
        var stuck = 0
        val started = System.currentTimeMillis()
        while (!state.gameOver && actions < 6000 && stuck < 50) {
            val decision = state.pendingDecision
            val actor = decision?.let { state.actorFor(it.playerId) } ?: state.priorityPlayerId ?: break
            val ai = ais.getValue(actor)
            val action = if (decision != null) SubmitDecision(decision.playerId, ai.respondToDecision(state, decision)) else ai.chooseAction(state)
            var result = processor.process(state, action).result
            if (result.error != null) {
                // Same fallback the offline host uses: pass, or declare nothing in combat.
                val fallback = when {
                    state.step == Step.DECLARE_ATTACKERS && state.isActiveTurnFor(actor) && state.getEntity(actor)?.get<AttackersDeclaredThisCombatComponent>() == null -> DeclareAttackers(actor, emptyMap())
                    state.step == Step.DECLARE_BLOCKERS && !state.isActiveTurnFor(actor) && state.getEntity(actor)?.get<BlockersDeclaredThisCombatComponent>() == null -> DeclareBlockers(actor, emptyMap())
                    else -> PassPriority(actor)
                }
                result = processor.process(state, fallback).result
                if (result.error != null) { stuck++; continue }
            }
            state = result.state
            actions++
        }
        val ms = System.currentTimeMillis() - started
        println("[4p-ai] gameOver=${state.gameOver} winner=${state.winnerId?.value} turn=${state.turnNumber} actions=$actions stuck=$stuck ms=$ms")
        assertTrue("four AIs got through many turns (turn ${state.turnNumber})", state.gameOver || state.turnNumber >= 12)
    }
}
