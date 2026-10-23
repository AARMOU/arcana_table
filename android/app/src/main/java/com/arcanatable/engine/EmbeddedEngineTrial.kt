package com.arcanatable.engine

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.tooling.CardLoader
import java.io.InputStream
import kotlin.random.Random

/**
 * Chunk 3 trial: Argentum's real rules engine and AI running inside Arcana with no server.
 * Cards arrive as DATA (Argentum's own exported JSON, one definition per line), so the app ships
 * no compiled card classes. Plays one AI-vs-AI game and reports how heavy that was.
 */
object EmbeddedEngineTrial {
    data class Result(
        val cards: Int,
        val loadMs: Long,
        val gameMs: Long,
        val turns: Int,
        val actions: Int,
        val finished: Boolean,
        val winner: String,
        val peakHeapMb: Long,
        val deckColors: String,
        val error: String? = null,
    ) {
        fun summary(): String = if (error != null) "Engine trial failed: $error" else
            "Engine trial: $cards cards loaded in $loadMs ms; AI vs AI ($deckColors) " +
                (if (finished) "finished" else "stopped") + " after $turns turns, $actions actions in $gameMs ms; winner $winner; peak heap $peakHeapMb MB"
    }

    fun loadCards(lines: Sequence<String>): List<CardDefinition> =
        lines.map(String::trim).filter(String::isNotEmpty).map(CardLoader::fromJsonPreservingIds).toList()

    /** A simple two-colour-free deck: 23 cheapest-first spells of [color] plus 17 matching basics. */
    fun monoDeck(cards: List<CardDefinition>, color: Color, random: Random): Deck {
        val basic = when (color) { Color.WHITE -> "Plains"; Color.BLUE -> "Island"; Color.BLACK -> "Swamp"; Color.RED -> "Mountain"; Color.GREEN -> "Forest" }
        val spells = cards.filter { !it.typeLine.isLand && it.colors == setOf(color) }.shuffled(random)
        val picks = (spells.filter { it.typeLine.isCreature }.take(15) + spells.filterNot { it.typeLine.isCreature }.take(8))
            .let { if (it.size < 23) it + spells.filterNot(it::contains).take(23 - it.size) else it }
        val names = picks.flatMap { listOf(it.name) } .let { list -> if (list.size < 23) list + List(23 - list.size) { i -> list[i % list.size] } else list }
        return Deck(names + List(17) { basic })
    }

    fun run(source: () -> InputStream, maxTurns: Int = 60, seed: Int = 7): Result {
        val runtime = Runtime.getRuntime()
        var peak = 0L
        fun sample() { peak = maxOf(peak, runtime.totalMemory() - runtime.freeMemory()) }
        return try {
            val loadStart = System.nanoTime()
            val cards = source().bufferedReader().useLines { loadCards(it) }
            val registry = CardRegistry().apply { register(cards) }
            val loadMs = (System.nanoTime() - loadStart) / 1_000_000
            sample()
            val random = Random(seed)
            val colors = listOf(Color.GREEN, Color.RED)
            val init = GameInitializer(registry).initializeGame(GameConfig(
                players = listOf(PlayerConfig("Green AI", monoDeck(cards, colors[0], random)), PlayerConfig("Red AI", monoDeck(cards, colors[1], random))),
                skipMulligans = true, startingPlayerIndex = 0,
            ))
            val processor = ActionProcessor(registry)
            val p1 = init.state.turnOrder[0]
            val p2 = init.state.turnOrder[1]
            val ai1 = AIPlayer.create(registry, p1)
            val ai2 = AIPlayer.create(registry, p2)
            var state: GameState = init.state
            var actions = 0
            val gameStart = System.nanoTime()
            while (!state.gameOver && state.turnNumber <= maxTurns && actions < 20_000) {
                val decision = state.pendingDecision
                val next: GameState? = if (decision != null) {
                    val ai = if (decision.playerId == p1) ai1 else ai2
                    val r = processor.process(state, SubmitDecision(decision.playerId, ai.respondToDecision(state, decision))).result
                    if (r.error != null) null else r.state
                } else when (state.priorityPlayerId) {
                    p1 -> ai1.playPriorityWindow(state, processor)
                    p2 -> ai2.playPriorityWindow(state, processor)
                    else -> null
                }
                actions++
                if (next == null) break
                state = next
                if (actions % 25 == 0) sample()
            }
            sample()
            val winner = when (state.winnerId) { p1 -> "Green AI"; p2 -> "Red AI"; null -> "none"; else -> "?" }
            Result(cards.size, loadMs, (System.nanoTime() - gameStart) / 1_000_000, state.turnNumber, actions, state.gameOver, winner, peak / (1024 * 1024), "green vs red")
        } catch (t: Throwable) {
            Result(0, 0, 0, 0, 0, false, "none", peak / (1024 * 1024), "", "${t.javaClass.simpleName}: ${t.message?.take(300)}")
        }
    }
}
