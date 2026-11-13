package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SanguinaryPriest
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class SanguinaryPriestIndependentCertificationTest {
    private val name = "Sanguinary Priest"

    private fun triggerScenario(vararg extraCards: String) =
        customScenario(SanguinaryPriest, "Grizzly Bears", "Serra Angel", *extraCards)

    private fun GameTestDriver.killAnotherCreatureWithCombat(target: EntityId): EntityId {
        val priest = putCreatureOnBattlefield(player1, name)
        val bear = putCreatureOnBattlefield(player1, "Grizzly Bears")
        val blocker = putCreatureOnBattlefield(player2, "Serra Angel")
        removeSummoningSickness(bear)
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(declareAttackers(player1, listOf(bear), player2).error)
        passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(declareBlockers(player2, mapOf(blocker to listOf(bear))).error)
        chooseBloodChaliceTarget(target)

        assertTrue(getGraveyard(player1).contains(bear))
        assertTrue(getPermanents(player1).contains(priest))
        return priest
    }

    private fun GameTestDriver.chooseBloodChaliceTarget(target: EntityId) {
        repeat(100) {
            when (val decision = state.pendingDecision) {
                is ChooseTargetsDecision -> {
                    assertNull(submitTargetSelection(decision.playerId, listOf(target)).error)
                    return
                }
                null -> state.priorityPlayerId?.let {
                    autoSubmitCombatDeclarationIfNeeded()
                    passPriority(it)
                }
                else -> autoResolveDecision()
            }
        }
        error("Blood Chalice did not request a target after another creature died")
    }

    @Test
    fun costsThreeGenericAndBlackHasLifelinkAndDealsTwoCombatDamage() {
        val d = customScenario(SanguinaryPriest)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.BLACK)

        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertTrue(d.getPermanents(p).contains(card))
        assertEquals(2, d.projected.getPower(card))
        assertEquals(4, d.projected.getToughness(card))
        assertTrue(d.projected.hasKeyword(card, Keyword.LIFELINK))

        d.removeSummoningSickness(card)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(card), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(22, d.getLifeTotal(p))
        assertEquals(18, d.getLifeTotal(d.player2))
    }

    @Test
    fun fourColorlessManaOrOnlyTwoGenericAndBlackCannotPayItsCost() {
        val noBlack = customScenario(SanguinaryPriest)
        val cardWithoutBlack = noBlack.putCardInHand(noBlack.player1, name)
        noBlack.giveColorlessMana(noBlack.player1, 4)

        assertNotNull(noBlack.castSpell(noBlack.player1, cardWithoutBlack).error)
        assertTrue(noBlack.zoneNames(noBlack.player1, Zone.HAND).contains(name))

        val shortGeneric = customScenario(SanguinaryPriest)
        val cardShortGeneric = shortGeneric.putCardInHand(shortGeneric.player1, name)
        shortGeneric.giveColorlessMana(shortGeneric.player1, 2)
        shortGeneric.giveMana(shortGeneric.player1, Color.BLACK)

        assertNotNull(shortGeneric.castSpell(shortGeneric.player1, cardShortGeneric).error)
        assertTrue(shortGeneric.zoneNames(shortGeneric.player1, Zone.HAND).contains(name))
    }

    @Test
    fun anotherCreatureYouControlDyingDealsOneToAnOpponentAndLifelinkGainsOne() {
        val d = triggerScenario()
        d.killAnotherCreatureWithCombat(d.player2)

        d.resolveAll()

        assertEquals(21, d.getLifeTotal(d.player1))
        assertEquals(19, d.getLifeTotal(d.player2))
    }

    @Test
    fun bloodChaliceCanTargetAndKillAnOpponentsCreature() {
        val d = triggerScenario("Savannah Lions")
        val lions = d.putCreatureOnBattlefield(d.player2, "Savannah Lions")
        d.killAnotherCreatureWithCombat(lions)

        d.resolveAll()

        assertTrue(d.getGraveyard(d.player2).contains(lions))
        assertEquals(21, d.getLifeTotal(d.player1))
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun bloodChaliceCanTargetAnOpponentsPlaneswalker() {
        val d = triggerScenario("Jace Beleren")
        val jace = d.putPermanentOnBattlefield(d.player2, "Jace Beleren")
        d.addComponent(jace, CountersComponent(mapOf(CounterType.LOYALTY to 3)))
        d.killAnotherCreatureWithCombat(jace)

        d.resolveAll()

        val loyalty = d.state.getEntity(jace)?.get<CountersComponent>()?.getCount(CounterType.LOYALTY) ?: 0
        assertEquals(2, loyalty)
        assertEquals(21, d.getLifeTotal(d.player1))
    }

    @Test
    fun anOpponentsCreatureDyingDoesNotTriggerBloodChalice() {
        val d = triggerScenario("Ornithopter")
        val priest = d.putCreatureOnBattlefield(d.player1, name)
        val serraAngel = d.putCreatureOnBattlefield(d.player1, "Serra Angel")
        val ornithopter = d.putCreatureOnBattlefield(d.player2, "Ornithopter")
        d.removeSummoningSickness(serraAngel)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(serraAngel), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(ornithopter to listOf(serraAngel))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        d.resolveAll(Choices(targets = listOf(d.player2)))

        assertTrue(d.getGraveyard(d.player2).contains(ornithopter))
        assertTrue(d.getPermanents(d.player1).contains(priest))
        assertEquals(20, d.getLifeTotal(d.player1))
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun thisCreatureDyingDoesNotTriggerBloodChalice() {
        val d = customScenario(SanguinaryPriest, "Colossal Dreadmaw")
        val priest = d.putCreatureOnBattlefield(d.player1, name)
        val dreadmaw = d.putCreatureOnBattlefield(d.player2, "Colossal Dreadmaw")
        d.removeSummoningSickness(dreadmaw)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)
        assertNull(d.declareAttackers(d.player2, listOf(dreadmaw), d.player1).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player1, mapOf(priest to listOf(dreadmaw))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        d.resolveAll(Choices(targets = listOf(d.player2)))

        assertTrue(d.getGraveyard(d.player1).contains(priest))
        assertEquals(20, d.getLifeTotal(d.player2))
    }
}
