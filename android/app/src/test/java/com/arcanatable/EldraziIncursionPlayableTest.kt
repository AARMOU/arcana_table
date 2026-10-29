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
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * The bundled original Eldrazi Incursion precon, as the app's offline runtime sees it: which of its
 * 100 slots are indexed by the shipped assets (not a rules certification), and an
 * AI-vs-AI game with it on Argentum's real engine. The library and game-end conditions
 * are mandatory assertions: missing assets or a stalled AI game must not silently pass.
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
        "Angelic Aberration", "Azlask, the Swelling Scourge", "Benthic Anomaly",
        "Hideous Taskmaster", "Mutated Cultist", "Opal Palace",
        "Twins of Discord",
    )

    @Test fun originalSlotIndexInventoryMatchesTheExplicitMissingList() {
        assertTrue("Offline library must be present; never skip Eldrazi acceptance tests", lib.isFullLibrary)
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
        assertTrue("Offline library must be present; never skip Eldrazi acceptance tests", lib.isFullLibrary)
        val (setup, registry) = OfflineGameSetup.build(lib, "You", names, commander, premades, Random(5))
        assertEquals(commander, setup.you.commander)
        assertEquals(99, setup.you.deck.size)
        listOf(
            "Selective Obliteration", "Forsaken Monument", "Eldrazi Confluence", "Kozilek's Return", "Inversion Behemoth",
            "Spawnbed Protector", "Ugin, the Ineffable", "Chittering Dispatcher", "Rishkar's Expertise",
            "Vile Redeemer", "Bismuth Mindrender", "Ulamog's Nullifier", "Morophon, the Boundless",
            "Eldrazi Temple", "Corrupted Crossroads",
        ).forEach { name ->
            assertNotNull("registered: $name", registry.getCard(name))
            assertTrue("in the deck: $name", name in setup.you.deck)
        }
        val leftOut = setup.notes.first { "Left out" in it }
        assertTrue(leftOut, "${expectedMissing.size} card" in leftOut)
    }

    /** Eldrazi Incursion mirror run by Argentum's AI on both sides: no engine stall or crash. */
    @Test fun incursionMirrorAiGame() {
        assertTrue("Offline library must be present; never skip Eldrazi acceptance tests", lib.isFullLibrary)
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
            var stepFailure: String? = null
            // Track real life-total progression to distinguish a long 40-life mirror from
            // a game that never deals damage. Record once per turn, not per AI action.
            fun lifeSnapshot(): String = state.turnOrder.joinToString(",") { seat ->
                "${seat.value}:${state.getEntity(seat)?.get<LifeTotalComponent>()?.life ?: "?"}"
            }
            val lifeByTurn = mutableListOf("${state.turnNumber}=${lifeSnapshot()}")
            var lastObservedTurn = state.turnNumber
            while (!state.gameOver && state.turnNumber <= 30 && actions < 8_000) {
                val decision = state.pendingDecision
                val next = if (decision != null) {
                    val r = processor.process(state, SubmitDecision(decision.playerId, ais.getValue(decision.playerId).respondToDecision(state, decision))).result
                    if (r.error != null) {
                        stepFailure = "SubmitDecision rejected: ${r.error}"
                        null
                    } else r.state
                } else {
                    val priority = state.priorityPlayerId
                    if (priority == null) {
                        stepFailure = "No priority player and no pending decision"
                        null
                    } else {
                        ais.getValue(priority).playPriorityWindow(state, processor)
                    }
                }
                actions++
                state = next ?: break
                if (state.turnNumber != lastObservedTurn) {
                    lastObservedTurn = state.turnNumber
                    lifeByTurn += "$lastObservedTurn=${lifeSnapshot()}"
                }
            }
            val stopReason = when {
                state.gameOver -> "game over"
                state.turnNumber > 30 -> "turn limit"
                actions >= 8_000 -> "action limit"
                else -> stepFailure ?: "unknown early exit"
            }
            val diagnostic = "seed=$seed turn=${state.turnNumber} actions=$actions over=${state.gameOver} " +
                "winner=${state.winnerId} priority=${state.priorityPlayerId} " +
                "pendingDecisionPlayer=${state.pendingDecision?.playerId} stop=$stopReason " +
                "ms=${(System.nanoTime() - started) / 1_000_000} " +
                "lifeByTurn=${lifeByTurn.joinToString(";")}"
            println("[eldrazi-incursion] $diagnostic")
            assertTrue("AI mirror must finish: $diagnostic", state.gameOver)
        }
    }
}
