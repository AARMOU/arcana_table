package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BredForTheHunt
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class BredForTheHuntIndependentCertificationTest {
    private val name = "Bred for the Hunt"

    private fun GameTestDriver.addPlusOnePlusOneCounter(creature: EntityId) {
        addComponent(creature, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
    }

    private fun GameTestDriver.dealUnblockedCombatDamage(
        attacker: EntityId,
        attackingPlayer: EntityId,
        defendingPlayer: EntityId,
    ) {
        removeSummoningSickness(attacker)
        passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = attackingPlayer)
        assertNull(declareAttackers(attackingPlayer, listOf(attacker), defendingPlayer).error)
        passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(declareBlockers(defendingPlayer, emptyMap()).error)
        passPriorityUntil(Step.COMBAT_DAMAGE)
    }

    @Test
    fun castingRequiresOneGenericOneGreenAndOneBlueMana() {
        val d = customScenario(BredForTheHunt)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveMana(p, Color.GREEN)

        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.getHand(p).contains(card))

        d.giveMana(p, Color.BLUE)
        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.getHand(p).contains(card))

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(card))
    }

    @Test
    fun yourCounteredCreatureDealingCombatDamageToAPlayerMayDraw() {
        val d = customScenario(BredForTheHunt, "Island", "Grizzly Bears")
        val p = d.player1
        val attacker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.addPlusOnePlusOneCounter(attacker)
        d.putPermanentOnBattlefield(p, name)
        d.putCardOnTopOfLibrary(p, "Island")
        val handBefore = d.getHandSize(p)

        d.dealUnblockedCombatDamage(attacker, p, d.player2)
        d.resolveAll(Choices(yes = true))

        assertEquals(17, d.getLifeTotal(d.player2))
        assertEquals(handBefore + 1, d.getHandSize(p))
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Island"))
        assertFalse(d.zoneNames(p, Zone.LIBRARY).contains("Island"))
    }

    @Test
    fun decliningTheOptionalDrawLeavesTheCardOnTopOfYourLibrary() {
        val d = customScenario(BredForTheHunt, "Island", "Grizzly Bears")
        val p = d.player1
        val attacker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.addPlusOnePlusOneCounter(attacker)
        d.putPermanentOnBattlefield(p, name)
        d.putCardOnTopOfLibrary(p, "Island")
        val handBefore = d.getHandSize(p)

        d.dealUnblockedCombatDamage(attacker, p, d.player2)
        d.resolveAll(Choices(yes = false))

        assertEquals(17, d.getLifeTotal(d.player2))
        assertEquals(handBefore, d.getHandSize(p))
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Island"))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Island"))
    }

    @Test
    fun aCreatureWithoutAPlusOnePlusOneCounterDoesNotTrigger() {
        val d = customScenario(BredForTheHunt, "Island", "Grizzly Bears")
        val p = d.player1
        val attacker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putPermanentOnBattlefield(p, name)
        d.putCardOnTopOfLibrary(p, "Island")
        val handBefore = d.getHandSize(p)

        d.dealUnblockedCombatDamage(attacker, p, d.player2)
        d.resolveAll(Choices(yes = true))

        assertEquals(18, d.getLifeTotal(d.player2))
        assertEquals(handBefore, d.getHandSize(p))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Island"))
    }

    @Test
    fun combatDamageToACreatureDoesNotTriggerTheDraw() {
        val d = customScenario(BredForTheHunt, "Island", "Grizzly Bears", "Llanowar Elves")
        val p = d.player1
        val opponent = d.player2
        val attacker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val blocker = d.putCreatureOnBattlefield(opponent, "Llanowar Elves")
        d.addPlusOnePlusOneCounter(attacker)
        d.putPermanentOnBattlefield(p, name)
        d.putCardOnTopOfLibrary(p, "Island")
        val handBefore = d.getHandSize(p)
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(attacker), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, mapOf(blocker to listOf(attacker))).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll(Choices(yes = true))

        assertEquals(20, d.getLifeTotal(opponent))
        assertEquals(handBefore, d.getHandSize(p))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Island"))
        assertFalse(d.getPermanents(opponent).contains(blocker))
    }

    @Test
    fun anOpponentsCounteredCreatureDealingCombatDamageToYouDoesNotTrigger() {
        val d = customScenario(BredForTheHunt, "Island", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val attacker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.addPlusOnePlusOneCounter(attacker)
        d.putPermanentOnBattlefield(p, name)
        d.putCardOnTopOfLibrary(p, "Island")
        val handBefore = d.getHandSize(p)

        d.dealUnblockedCombatDamage(attacker, opponent, p)
        d.resolveAll(Choices(yes = true))

        assertEquals(17, d.getLifeTotal(p))
        assertEquals(handBefore, d.getHandSize(p))
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Island"))
    }
}
