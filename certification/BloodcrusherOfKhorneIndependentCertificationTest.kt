package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BloodcrusherOfKhorne
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class BloodcrusherOfKhorneIndependentCertificationTest {
    @Test
    fun castingWithThreeGenericAndOneRedEntersAsAThreeThreeWithTrample() {
        val d = customScenario(BloodcrusherOfKhorne)
        val player = d.player1
        val crusher = d.putCardInHand(player, "Bloodcrusher of Khorne")
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.RED)

        assertNull(d.castSpell(player, crusher).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(crusher))
        assertEquals(3, d.projected.getPower(crusher))
        assertEquals(3, d.projected.getToughness(crusher))
        assertTrue(d.projected.hasKeyword(crusher, Keyword.TRAMPLE))
    }

    @Test
    fun fourColorlessManaCannotPayItsRedManaSymbol() {
        val d = customScenario(BloodcrusherOfKhorne)
        val player = d.player1
        val crusher = d.putCardInHand(player, "Bloodcrusher of Khorne")
        d.giveColorlessMana(player, 4)

        assertNotNull(d.castSpell(player, crusher).error)

        assertTrue(d.getHand(player).contains(crusher))
        assertFalse(d.getPermanents(player).contains(crusher))
    }

    @Test
    fun twoGenericAndOneRedManaCannotPayItsThreeGenericManaCost() {
        val d = customScenario(BloodcrusherOfKhorne)
        val player = d.player1
        val crusher = d.putCardInHand(player, "Bloodcrusher of Khorne")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.RED)

        assertNotNull(d.castSpell(player, crusher).error)

        assertTrue(d.getHand(player).contains(crusher))
        assertFalse(d.getPermanents(player).contains(crusher))
    }

    @Test
    fun bloodcrusherTrampleDealsExcessCombatDamagePastABlocker() {
        val d = customScenario(BloodcrusherOfKhorne, "Llanowar Elves")
        val crusher = d.putCreatureOnBattlefield(d.player1, "Bloodcrusher of Khorne")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Llanowar Elves")
        d.removeSummoningSickness(crusher)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(crusher), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(crusher))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(18, d.getLifeTotal(d.player2))
        assertFalse(d.getPermanents(d.player2).contains(blocker))
        assertTrue(d.getPermanents(d.player1).contains(crusher))
    }

    @Test
    fun otherCreaturesYouControlHaveTrampleAndDealExcessDamage() {
        val d = customScenario(BloodcrusherOfKhorne, "Grizzly Bears", "Llanowar Elves")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Llanowar Elves")
        d.putCreatureOnBattlefield(player, "Bloodcrusher of Khorne")
        d.removeSummoningSickness(bear)

        assertTrue(d.projected.hasKeyword(bear, Keyword.TRAMPLE))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(bear), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(bear))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(19, d.getLifeTotal(d.player2))
        assertFalse(d.getPermanents(d.player2).contains(blocker))
        assertTrue(d.getPermanents(player).contains(bear))
    }

    @Test
    fun bloodcrusherDoesNotGrantTrampleToOpponentsCreatures() {
        val d = customScenario(BloodcrusherOfKhorne, "Grizzly Bears", "Llanowar Elves")
        val crusher = d.putCreatureOnBattlefield(d.player1, "Bloodcrusher of Khorne")
        val attacker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val blocker = d.putCreatureOnBattlefield(d.player1, "Llanowar Elves")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)

        assertFalse(d.projected.hasKeyword(attacker, Keyword.TRAMPLE))
        assertNull(d.declareAttackers(d.player2, listOf(attacker), d.player1).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player1, mapOf(blocker to listOf(attacker))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(20, d.getLifeTotal(d.player1))
        assertFalse(d.getPermanents(d.player1).contains(blocker))
        assertTrue(d.getPermanents(d.player2).contains(attacker))
        assertTrue(d.getPermanents(d.player1).contains(crusher))
    }
}
