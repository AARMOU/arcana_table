package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MutatedCultist
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class MutatedCultistIndependentCertificationTest {
    private val name = "Mutated Cultist"

    private fun GameTestDriver.counterCount(entity: EntityId, type: CounterType): Int =
        state.getEntity(entity)?.get<CountersComponent>()?.getCount(type) ?: 0

    private fun castCultist(d: GameTestDriver, player: EntityId): EntityId {
        val card = d.putCardInHand(player, name)
        d.giveMana(player, Color.BLACK)
        d.giveColorlessMana(player, 2)
        assertNull(d.castSpell(player, card).error)
        return card
    }

    @Test
    fun castingCostsTwoGenericAndBlackAndDevoidLeavesItColorless() {
        val d = customScenario(MutatedCultist)
        val player = d.player1
        val card = d.putCardInHand(player, name)
        d.giveMana(player, Color.BLACK)
        d.giveColorlessMana(player, 1)

        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.getHand(player).contains(card))

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(card))
        assertTrue(d.projected.getColors(card).isEmpty())
        assertEquals(1, d.projected.getPower(card))
        assertEquals(3, d.projected.getToughness(card))
    }

    @Test
    fun threeGenericManaCannotPayTheBlackSymbol() {
        val d = customScenario(MutatedCultist)
        val player = d.player1
        val card = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 3)

        assertNotNull(d.castSpell(player, card).error)

        assertTrue(d.getHand(player).contains(card))
        assertTrue(d.permanentsNamed(player, name).isEmpty())
    }

    @Test
    fun allCountersOnATargetPermanentAreRemovedAndReduceOnlyTheNextSpellByTheirNumber() {
        val d = customScenario(MutatedCultist, "Grizzly Bears", "Giant Spider")
        val player = d.player1
        val target = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.addComponent(
            target,
            CountersComponent(
                mapOf(
                    CounterType.PLUS_ONE_PLUS_ONE to 1,
                    CounterType.CHARGE to 2,
                ),
            ),
        )

        castCultist(d, player)
        d.resolveAll(Choices(targets = listOf(target)))

        assertEquals(0, d.counterCount(target, CounterType.PLUS_ONE_PLUS_ONE))
        assertEquals(0, d.counterCount(target, CounterType.CHARGE))

        val spider = d.putCardInHand(player, "Giant Spider")
        d.giveMana(player, Color.GREEN)
        assertNull(d.castSpell(player, spider).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(player, "Giant Spider").contains(spider))

        val bears = d.putCardInHand(player, "Grizzly Bears")
        d.giveMana(player, Color.GREEN)
        assertNotNull(d.castSpell(player, bears).error)
        assertTrue(d.getHand(player).contains(bears))
    }

    @Test
    fun anOpponentIsATargetAndTheirCountersAreRemoved() {
        val d = customScenario(MutatedCultist, "Giant Spider")
        val player = d.player1
        val opponent = d.player2
        d.addComponent(opponent, CountersComponent(mapOf(CounterType.CHARGE to 2)))

        castCultist(d, player)
        d.resolveAll(Choices(targets = listOf(opponent)))

        assertEquals(0, d.counterCount(opponent, CounterType.CHARGE))

        val spider = d.putCardInHand(player, "Giant Spider")
        d.giveMana(player, Color.GREEN)
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, spider).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(player, "Giant Spider").contains(spider))
    }

    @Test
    fun choosingNoTargetRemovesNoCountersAndProvidesNoCostReduction() {
        val d = customScenario(MutatedCultist, "Grizzly Bears")
        val player = d.player1
        castCultist(d, player)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, name).isNotEmpty())

        val bears = d.putCardInHand(player, "Grizzly Bears")
        d.giveMana(player, Color.GREEN)
        assertNotNull(d.castSpell(player, bears).error)
        assertTrue(d.getHand(player).contains(bears))
    }

    @Test
    fun puttingMutatedCultistDirectlyOntoTheBattlefieldDoesNotTriggerItsCastAbility() {
        val d = customScenario(MutatedCultist, "Grizzly Bears")
        val player = d.player1
        val target = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.addComponent(target, CountersComponent(mapOf(CounterType.CHARGE to 1)))

        d.putCreatureOnBattlefield(player, name)

        assertEquals(1, d.counterCount(target, CounterType.CHARGE))

        val bears = d.putCardInHand(player, "Grizzly Bears")
        d.giveMana(player, Color.GREEN)
        assertNotNull(d.castSpell(player, bears).error)
        assertTrue(d.getHand(player).contains(bears))
    }

    @Test
    fun theCostReductionExpiresAtTheEndOfTheTurnIfNoSpellIsCast() {
        val d = customScenario(MutatedCultist, "Grizzly Bears", "Giant Spider")
        val player = d.player1
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.addComponent(target, CountersComponent(mapOf(CounterType.CHARGE to 1)))

        castCultist(d, player)
        d.resolveAll(Choices(targets = listOf(target)))
        assertEquals(0, d.counterCount(target, CounterType.CHARGE))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = player)

        val spider = d.putCardInHand(player, "Giant Spider")
        d.giveMana(player, Color.GREEN)
        assertNotNull(d.castSpell(player, spider).error)
        assertTrue(d.getHand(player).contains(spider))
    }

    @Test
    fun deathtouchMakesOneCombatDamageLethalToTheBlockingCreature() {
        val d = customScenario(MutatedCultist, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val attacker = d.putCreatureOnBattlefield(player, name)
        val blocker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        assertTrue(d.projected.hasKeyword(attacker, Keyword.DEATHTOUCH))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(attacker), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, mapOf(blocker to listOf(attacker))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertFalse(d.getPermanents(opponent).contains(blocker))
        assertTrue(d.getGraveyardCardNames(opponent).contains("Grizzly Bears"))
        assertTrue(d.getPermanents(player).contains(attacker))
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains(name))
    }
}
