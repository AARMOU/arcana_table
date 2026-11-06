package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EldraziConscription
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class EldraziConscriptionIndependentCertificationTest {
    @Test
    fun eightGenericManaCastsTheAuraAndGrantsTenPowerTenToughnessAndTrample() {
        val d = customScenario(EldraziConscription, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val aura = d.putCardInHand(player, "Eldrazi Conscription")
        d.giveColorlessMana(player, 8)

        assertNull(d.castSpell(player, aura, listOf(bear)).error)
        d.resolveAll()

        assertEquals(12, d.projected.getPower(bear))
        assertEquals(12, d.projected.getToughness(bear))
        assertTrue(d.projected.hasKeyword(bear, Keyword.TRAMPLE))
    }

    @Test
    fun sevenGenericManaCannotPayTheCastingCost() {
        val d = customScenario(EldraziConscription, "Grizzly Bears")
        val player = d.player1
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val aura = d.putCardInHand(player, "Eldrazi Conscription")
        d.giveColorlessMana(player, 7)

        assertNotNull(d.castSpell(player, aura, listOf(bear)).error)

        assertTrue(d.getHand(player).contains(aura))
        assertEquals(2, d.projected.getPower(bear))
        assertEquals(2, d.projected.getToughness(bear))
    }

    @Test
    fun theAuraCannotEnchantANoncreaturePermanent() {
        val d = customScenario(EldraziConscription, "Sol Ring")
        val player = d.player1
        val ring = d.putPermanentOnBattlefield(player, "Sol Ring")
        val aura = d.putCardInHand(player, "Eldrazi Conscription")
        d.giveColorlessMana(player, 8)

        assertNotNull(d.castSpell(player, aura, listOf(ring)).error)

        assertTrue(d.getHand(player).contains(aura))
        assertTrue(d.getPermanents(player).contains(ring))
    }

    @Test
    fun theEnchantedCreatureTriggersAnnihilatorTwoWhenItAttacks() {
        val d = customScenario(EldraziConscription, "Grizzly Bears", "Plains")
        val player = d.player1
        val opponent = d.player2
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val aura = d.putCardInHand(player, "Eldrazi Conscription")
        repeat(4) { d.putPermanentOnBattlefield(opponent, "Plains") }
        d.giveColorlessMana(player, 8)
        assertNull(d.castSpell(player, aura, listOf(bear)).error)
        d.resolveAll()
        assertEquals("Annihilator triggers on attack, not when the Aura resolves",
            4, d.permanentsNamed(opponent, "Plains").size)

        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(bear), opponent).error)
        d.resolveAll()

        assertEquals("Annihilator 2 makes the defending player sacrifice two permanents",
            2, d.permanentsNamed(opponent, "Plains").size)
    }

    @Test
    fun trampleAssignsExcessDamagePastTheRemainingBlocker() {
        val d = customScenario(EldraziConscription, "Grizzly Bears", "Llanowar Elves")
        val player = d.player1
        val opponent = d.player2
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val aura = d.putCardInHand(player, "Eldrazi Conscription")
        repeat(3) { d.putCreatureOnBattlefield(opponent, "Llanowar Elves") }
        d.giveColorlessMana(player, 8)
        assertNull(d.castSpell(player, aura, listOf(bear)).error)
        d.resolveAll()
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(bear), opponent).error)
        d.resolveAll()
        val blocker = d.permanentsNamed(opponent, "Llanowar Elves").single()
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, mapOf(blocker to listOf(bear))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(9, d.getLifeTotal(opponent))
        assertFalse(d.getPermanents(opponent).contains(blocker))
    }
}
