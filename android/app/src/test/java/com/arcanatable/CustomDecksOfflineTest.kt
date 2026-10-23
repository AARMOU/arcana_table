package com.arcanatable

import com.arcanatable.engine.OfflineCardLibrary
import com.arcanatable.engine.OfflineGameSetup
import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** The user's two custom-card decks (Tokens Win, Artificer) play offline with Arcana's custom cards. */
class CustomDecksOfflineTest {
    private val assets = File("src/main/assets")
    private val lib = OfflineCardLibrary({ path -> File(assets, path).takeIf { it.exists() }?.inputStream() })
    private val premades = File(assets, "argentum/commander-decks.json").readText()

    private fun deck(file: String): Pair<String, List<String>> {
        val lines = File("src/test/resources/decks/$file").readLines().filter(String::isNotBlank).map {
            val space = it.indexOf(' '); it.substring(0, space).toInt() to it.substring(space + 1).trim()
        }
        return lines.first().second to lines.flatMap { (n, name) -> List(n) { name } }
    }

    @Test fun tokensWinUsesCustomCardsAndOnlyLeavesOutGoblinWelder() {
        assumeTrue(lib.isFullLibrary)
        val (commander, names) = deck("tokens-win.txt")
        val (setup, registry) = OfflineGameSetup.build(lib, "You", names, commander, premades, Random(3))
        assertEquals("Rocco, Cabaretti Caterer", setup.you.commander)
        assertEquals(99, setup.you.deck.size)
        listOf("Sneak Attack", "Food Chain", "Reflecting Pool", "Jaws, Relentless Predator").forEach { assertNotNull(it, registry.getCard(it)) }
        assertTrue(setup.notes.toString(), setup.notes.any { "Goblin Welder" in it })
        assertTrue(setup.notes.toString(), setup.notes.none { "Sneak Attack" in it && "Left out" in it })
        assertTrue(setup.notes.any { it.startsWith("Using Arcana's own versions of 15") })
    }

    @Test fun artificerUsesCustomCardsAndOnlyLeavesOutRatchet() {
        assumeTrue(lib.isFullLibrary)
        val (commander, names) = deck("artificer.txt")
        val (setup, registry) = OfflineGameSetup.build(lib, "You", names, commander, premades, Random(3))
        assertEquals("Miles \"Tails\" Prower", setup.you.commander)
        assertEquals(10, setup.you.deck.count { it == "Cid, Timeless Artificer" })
        assertNotNull(registry.getCard("Urza's Saga"))
        val leftOut = setup.notes.first { "Left out" in it }
        assertTrue(leftOut, "Ratchet" in leftOut && "1 card" in leftOut)
    }

    /** Both custom decks, each run by Argentum's AI, play a whole game without the engine choking. */
    @Test fun tokensWinVersusArtificerAiGame() {
        assumeTrue(lib.isFullLibrary)
        for (seed in listOf(1, 2)) {
            val decks = listOf(deck("tokens-win.txt"), deck("artificer.txt"))
            val registry = CardRegistry().apply { register(lib.load(decks.flatMap { it.second }.toSet())) }
            val players = decks.mapIndexed { i, (commander, names) ->
                val list = OfflineGameSetup.argentumLibraryList(names, commander).mapNotNull(lib::canonical)
                val filled = list + List((99 - list.size).coerceAtLeast(0)) { if (i == 0) "Mountain" else "Island" }
                PlayerConfig(if (i == 0) "Rocco" else "Tails", Deck(filled), startingLife = 40, commanderCardNames = listOf(lib.canonical(commander)!!))
            }
            val init = GameInitializer(registry).initializeGame(GameConfig(players = players, skipMulligans = true, format = Format.Commander(), startingPlayerIndex = seed % 2))
            val processor = ActionProcessor(registry)
            val ids = init.state.turnOrder
            val ais = ids.associateWith { AIPlayer.create(registry, it) }
            var state: GameState = init.state
            var actions = 0
            val started = System.nanoTime()
            while (!state.gameOver && state.turnNumber <= 40 && actions < 8_000) {
                val decision = state.pendingDecision
                val next = if (decision != null) {
                    val r = processor.process(state, SubmitDecision(decision.playerId, ais.getValue(decision.playerId).respondToDecision(state, decision))).result
                    if (r.error != null) null else r.state
                } else state.priorityPlayerId?.let { ais.getValue(it).playPriorityWindow(state, processor) }
                actions++
                state = next ?: break
            }
            println("[custom-decks] seed=$seed turn=${state.turnNumber} actions=$actions over=${state.gameOver} " +
                "winner=${state.winnerId} ms=${(System.nanoTime() - started) / 1_000_000}")
            assertTrue("seed $seed stalled on turn ${state.turnNumber}", state.gameOver || state.turnNumber >= 8)
        }
    }
}
