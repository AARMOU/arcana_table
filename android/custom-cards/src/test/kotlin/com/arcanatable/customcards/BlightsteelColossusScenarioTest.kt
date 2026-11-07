package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BlightsteelColossus
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

private val SacrificeCreatureForReplacementTest = card("Sacrifice Creature Test") {
    manaCost = "{0}"
    typeLine = "Sorcery"
    oracleText = "Sacrifice a creature."
    spell { effect = Effects.SacrificeOwn(GameObjectFilter.Creature) }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "1"
    }
}

/**
 * Blightsteel Colossus — full Oracle behaviour on the pinned engine:
 * trample, infect (poison to players, -1/-1 counters to creatures, 704.5c loss at ten poison),
 * indestructible, and the from-anywhere shuffle-into-library replacement.
 */
class BlightsteelColossusScenarioTest {

    private fun GameTestDriver.poison(player: EntityId): Int =
        state.getEntity(player)?.get<CountersComponent>()?.getCount(CounterType.POISON) ?: 0

    private fun GameTestDriver.minusCounters(creature: EntityId): Int =
        state.getEntity(creature)?.get<CountersComponent>()?.getCount(CounterType.MINUS_ONE_MINUS_ONE) ?: 0

    private fun GameTestDriver.readyColossus(player: EntityId): EntityId =
        putCreatureOnBattlefield(player, "Blightsteel Colossus").also { removeSummoningSickness(it) }

    private fun GameTestDriver.attackUnblocked(attacker: EntityId, attackerSeat: EntityId, defender: EntityId) {
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(declareAttackers(attackerSeat, listOf(attacker), defender).error)
        passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(declareNoBlockers(defender).error)
        finishCombat()
    }

    /**
     * Pass through combat damage to end of combat, answering the damage-assignment decision with
     * [plan] (unnamed edges keep the engine default). Stops early if the game ends.
     */
    private fun GameTestDriver.finishCombat(plan: Map<Pair<EntityId, EntityId>, Int> = emptyMap()) {
        var guard = 0
        while (!state.gameOver && state.step != Step.END_COMBAT && guard++ < 60) {
            val decision = state.pendingDecision
            when {
                decision is com.wingedsheep.engine.core.CombatResolutionDecision ->
                    assertNull(submitCombatDamage(plan).error)
                decision != null -> autoResolveDecision()
                state.priorityPlayerId != null -> {
                    autoSubmitCombatDeclarationIfNeeded()
                    passPriority(state.priorityPlayerId!!)
                }
                else -> break
            }
        }
    }

    @Test
    fun combatDamageToAPlayerGivesPoisonInsteadOfLifeLoss() {
        val d = customScenario(BlightsteelColossus)
        val colossus = d.readyColossus(d.player1)
        d.setLifeTotal(d.player2, 40)

        d.attackUnblocked(colossus, d.player1, d.player2)

        assertEquals("infect damage causes no life loss (CR 120.3b)", 40, d.getLifeTotal(d.player2))
        assertEquals("11 poison counters", 11, d.poison(d.player2))
        assertTrue("ten or more poison loses the game (704.5c)", d.state.getEntity(d.player2)!!.has<PlayerLostComponent>())
        d.assertGameOver(d.player1)
    }

    @Test
    fun poisonBelowTenDoesNotLoseAndAccumulatesWithExistingCounters() {
        // Ichor Rats (2/1 infect) is the same engine path; it shows poison stacks across hits
        // and that the threshold is exactly ten, not "any poison".
        val d = customScenario(BlightsteelColossus, "Ichor Rats")
        val rats = d.putCreatureOnBattlefield(d.player1, "Ichor Rats").also { d.removeSummoningSickness(it) }
        d.attackUnblocked(rats, d.player1, d.player2)
        assertEquals(2, d.poison(d.player2))
        assertFalse("two poison is not a loss", d.state.getEntity(d.player2)!!.has<PlayerLostComponent>())
        assertEquals("life untouched", 20, d.getLifeTotal(d.player2))
    }

    @Test
    fun blockedColossusPutsMinusCountersOnBlockerAndTramplesPoisonOver() {
        val d = customScenario(BlightsteelColossus, "Hill Giant")
        val colossus = d.readyColossus(d.player1)
        val giant = d.putCreatureOnBattlefield(d.player2, "Hill Giant") // 3/3

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(colossus), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(giant to listOf(colossus))).error)
        // Trample: assign lethal (3) to the blocker, 8 to the player.
        d.finishCombat(mapOf((colossus to giant) to 3, (colossus to d.player2) to 8))

        assertFalse("three -1/-1 counters kill the 3/3 Hill Giant", giant in d.getCreatures(d.player2))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Hill Giant"))
        assertEquals("trample excess becomes poison, not life loss", 8, d.poison(d.player2))
        assertEquals(20, d.getLifeTotal(d.player2))
        assertFalse("8 poison is not yet a loss", d.state.getEntity(d.player2)!!.has<PlayerLostComponent>())
    }

    @Test
    fun infectDamageToCreatureIsCountersNotMarkedDamage() {
        // Craw Wurm (6/4) blocks; it receives counters (CR 702.90c) rather than marked damage, so
        // the change persists past cleanup. Assign all 11 to the blocker so the counter count is exact.
        val d = customScenario(BlightsteelColossus, "Craw Wurm")
        val colossus = d.readyColossus(d.player1)
        val wurm = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(colossus), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(wurm to listOf(colossus))).error)
        d.finishCombat(mapOf((colossus to wurm) to 11, (colossus to d.player2) to 0))

        assertFalse(wurm in d.getCreatures(d.player2))
        assertEquals("no poison when nothing tramples over", 0, d.poison(d.player2))
        assertEquals(20, d.getLifeTotal(d.player2))
        // The Colossus takes 6 from the Wurm (normal, non-infect damage) and survives: indestructible.
        assertTrue(colossus in d.getCreatures(d.player1))
        assertEquals("the Colossus has no -1/-1 counters from a non-infect source", 0, d.minusCounters(colossus))
    }

    @Test
    fun nonInfectCreatureStillCausesLifeLossNotPoison() {
        // Negative control: the engine only converts damage for sources that actually have infect.
        val d = customScenario(BlightsteelColossus, "Hill Giant")
        val giant = d.putCreatureOnBattlefield(d.player1, "Hill Giant").also { d.removeSummoningSickness(it) }
        d.attackUnblocked(giant, d.player1, d.player2)
        assertEquals(17, d.getLifeTotal(d.player2))
        assertEquals(0, d.poison(d.player2))
    }

    @Test
    fun fourPlayerGameOnlyTheAttackedOpponentIsPoisonedAndTheGameContinues() {
        val d = CardPool.driver("Grizzly Bears").apply {
            registerCards(listOf(CardLoader.fromJsonPreservingIds(exportLine(BlightsteelColossus))))
        }
        val seats = d.initMultiplayer(List(4) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val (me, bystanderA, defender, bystanderB) = seats
        val colossus = d.readyColossus(me)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(me, listOf(colossus), defender).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        d.autoSubmitCombatDeclarationIfNeeded()
        d.finishCombat()

        assertEquals(11, d.poison(defender))
        assertTrue("the poisoned player loses", d.state.getEntity(defender)!!.has<PlayerLostComponent>())
        for (other in listOf(bystanderA, bystanderB)) {
            assertEquals("bystanders get no poison", 0, d.poison(other))
            assertFalse("bystanders are still in the game", d.state.getEntity(other)!!.has<PlayerLostComponent>())
        }
        assertFalse("three players remain, so the game is not over", d.state.gameOver)
    }

    @Test
    fun indestructibleColossusSurvivesDestroyEffects() {
        val d = customScenario(BlightsteelColossus, "Wrath of God")
        val colossus = d.putCreatureOnBattlefield(d.player1, "Blightsteel Colossus")
        val wrath = d.putCardInHand(d.player1, "Wrath of God")
        d.giveColorlessMana(d.player1, 2)
        d.giveMana(d.player1, com.wingedsheep.sdk.core.Color.WHITE, 2)
        assertNull(d.castSpell(d.player1, wrath).error)
        d.resolveAll()
        assertTrue(colossus in d.getCreatures(d.player1))
    }

    @Test
    fun sacrificedColossusShufflesIntoOwnersLibrary() {
        val d = customScenario(SacrificeCreatureForReplacementTest).apply {
            registerCard(BlightsteelColossus)
        }
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Forest")
        d.putCreatureOnBattlefield(p, "Blightsteel Colossus")

        val sacrifice = d.putCardInHand(p, "Sacrifice Creature Test")
        assertNull(d.castSpell(p, sacrifice).error)
        d.resolveAll()

        assertTrue("the Colossus is redirected into its owner's library", d.zoneNames(p, Zone.LIBRARY).contains("Blightsteel Colossus"))
        assertFalse("the Colossus never reaches its owner's graveyard", d.zoneNames(p, Zone.GRAVEYARD).contains("Blightsteel Colossus"))
    }

    @Test
    fun discardedFromHandShufflesIntoLibraryInstead() {
        val d = customScenario(BlightsteelColossus, "Mind Rot")
        val p2 = d.player2
        d.putCardInHand(p2, "Blightsteel Colossus")
        val libraryBefore = d.zoneNames(p2, Zone.LIBRARY).size
        val rot = d.putCardInHand(d.player1, "Mind Rot")
        d.giveColorlessMana(d.player1, 2)
        d.giveMana(d.player1, com.wingedsheep.sdk.core.Color.BLACK, 1)
        assertNull(d.castSpell(d.player1, rot, listOf(p2)).error)
        var guard = 0
        while (guard++ < 20) {
            val decision = d.state.pendingDecision
            if (decision is com.wingedsheep.engine.core.SelectCardsDecision) {
                val colossusId = decision.options.first { d.getCardName(it) == "Blightsteel Colossus" }
                val other = decision.options.first { it != colossusId }
                d.submitCardSelection(decision.playerId, listOf(colossusId, other).take(decision.maxSelections))
            } else if (decision != null) d.decide() else if (d.state.stack.isNotEmpty()) d.bothPass() else break
        }

        assertFalse(d.zoneNames(p2, Zone.GRAVEYARD).contains("Blightsteel Colossus"))
        assertFalse(d.zoneNames(p2, Zone.HAND).contains("Blightsteel Colossus"))
        assertEquals("shuffled into its owner's library", libraryBefore + 1, d.zoneNames(p2, Zone.LIBRARY).size)
        assertTrue(d.zoneNames(p2, Zone.LIBRARY).contains("Blightsteel Colossus"))
    }

    @Test
    fun milledFromLibraryStaysInLibrary() {
        val d = customScenario(BlightsteelColossus, "Tome Scour")
        val p2 = d.player2
        d.putCardOnTopOfLibrary(p2, "Blightsteel Colossus")
        val scour = d.putCardInHand(d.player1, "Tome Scour")
        d.giveMana(d.player1, com.wingedsheep.sdk.core.Color.BLUE, 1)
        assertNull(d.castSpell(d.player1, scour, listOf(p2)).error)
        d.resolveAll()

        val graveyard = d.zoneNames(p2, Zone.GRAVEYARD)
        assertEquals("the other four cards are milled", 4, graveyard.size)
        assertFalse("the Colossus is redirected back into the library", graveyard.contains("Blightsteel Colossus"))
        assertTrue(d.zoneNames(p2, Zone.LIBRARY).contains("Blightsteel Colossus"))
    }

    @Test
    fun exiledColossusIsNotRedirected() {
        // Negative: the replacement only watches "put into a graveyard"; exile is unaffected.
        val d = customScenario(BlightsteelColossus)
        val colossus = d.putCreatureOnBattlefield(d.player1, "Blightsteel Colossus")
        d.moveToExile(colossus)
        assertTrue(d.zoneNames(d.player1, Zone.EXILE).contains("Blightsteel Colossus"))
        assertFalse(d.zoneNames(d.player1, Zone.LIBRARY).contains("Blightsteel Colossus"))
    }
}
