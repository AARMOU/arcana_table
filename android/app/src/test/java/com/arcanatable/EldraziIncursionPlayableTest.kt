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

/**
 * The bundled original Eldrazi Incursion precon, as the app's offline runtime sees it: which of its
 * 100 slots are indexed by the shipped assets (not a rules certification), and an
 * AI-vs-AI game with it on Argentum's real engine.
 */
class EldraziIncursionPlayableTest {
    private val assets = File("src/main/assets")
    private val lib = OfflineCardLibrary({ path -> File(assets, path).takeIf { it.exists() }?.inputStream() })
    private val premades = File(assets, "argentum/commander-decks.json").readText()
    private val entries = parseDeckList(File(assets, "precons/eldrazi_incursion.txt").readText())
    private val commander = entries.single { it.commander }.name
    private val names = entries.flatMap { e -> List(e.count) { e.name } }

    /** Originals the pinned engine can't play yet; each is a recorded blocker or unimplemented. */
    private val expectedMissing = setOf(
        "Angelic Aberration", "Azlask, the Swelling Scourge", "Benthic Anomaly", "Corrupted Crossroads",
        "Eldrazi Temple", "Hideous Taskmaster", "Mutated Cultist", "Opal Palace",
        "Twins of Discord",
    )

    @Test fun originalSlotIndexInventoryMatchesTheExplicitMissingList() {
        assumeTrue(lib.isFullLibrary)
        assertEquals(100, names.size)
        assertEquals("Ulalek, Fused Atrocity", commander)
        val indexed = names.filter { lib.canonical(it) != null }
        val missing = names.filter { lib.canonical(it) == null }
        assertEquals(expectedMissing, missing.toSet())
        assertEquals("one copy each", missing.size, missing.toSet().size)
        assertTrue("indexed slots ${indexed.size}", indexed.size >= 90)
        assertEquals("All original slots accounted for, without substitutions", 100 - expectedMissing.size, indexed.size)
    }

    @Test fun newlyImplementedCardsLoadIntoTheOfflineGame() {
        assumeTrue(lib.isFullLibrary)
        val (setup, registry) = OfflineGameSetup.build(lib, "You", names, commander, premades, Random(5))
        assertEquals(commander, setup.you.commander)
        assertEquals(99, setup.you.deck.size)
        listOf(
            "Selective Obliteration", "Forsaken Monument", "Eldrazi Confluence", "Kozilek's Return", "Inversion Behemoth",
            "Spawnbed Protector", "Ugin, the Ineffable", "Chittering Dispatcher", "Rishkar's Expertise",
            "Vile Redeemer", "Bismuth Mindrender", "Ulamog's Nullifier", "Morophon, the Boundless",
        ).forEach { name ->
            assertNotNull("registered: $name", registry.getCard(name))
            assertTrue("in the deck: $name", name in setup.you.deck)
        }
        val leftOut = setup.notes.first { "Left out" in it }
        assertTrue(leftOut, "${expectedMissing.size} card" in leftOut)
    }

    /** Eldrazi Incursion mirror run by Argentum's AI on both sides: no engine stall or crash. */
    @Test fun incursionMirrorAiGame() {
        assumeTrue(lib.isFullLibrary)
        val list = OfflineGameSetup.argentumLibraryList(names, commander).mapNotNull(lib::canonical)
        val registry = CardRegistry().apply { register(lib.load(names.toSet())) }
        for (seed in listOf(1, 2, 3)) {
            val players = (0..1).map { i ->
                PlayerConfig(if (i == 0) "Incursion A" else "Incursion B", Deck(list.shuffled(Random(seed * 10 + i))),
                    startingLife = 40, commanderCardNames = listOf(lib.canonical(commander)!!))
            }
            val init = GameInitializer(registry).initializeGame(GameConfig(players = players, skipMulligans = true, format = Format.Commander(), startingPlayerIndex = seed % 2))
            val processor = ActionProcessor(registry)
            val ais = init.state.turnOrder.associateWith { AIPlayer.create(registry, it) }
            var state: GameState = init.state
            var actions = 0
            val started = System.nanoTime()
            while (!state.gameOver && state.turnNumber <= 30 && actions < 8_000) {
                val decision = state.pendingDecision
                val next = if (decision != null) {
                    val r = processor.process(state, SubmitDecision(decision.playerId, ais.getValue(decision.playerId).respondToDecision(state, decision))).result
                    if (r.error != null) null else r.state
                } else state.priorityPlayerId?.let { ais.getValue(it).playPriorityWindow(state, processor) }
                actions++
                state = next ?: break
            }
            println("[eldrazi-incursion] seed=$seed turn=${state.turnNumber} actions=$actions over=${state.gameOver} " +
                "winner=${state.winnerId} ms=${(System.nanoTime() - started) / 1_000_000}")
            assertTrue("seed $seed stalled on turn ${state.turnNumber}", state.gameOver || state.turnNumber >= 8)
        }
    }
}
