package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BaneOfBalaGed
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class BaneOfBalaGedIndependentCertificationTest {
    private fun GameTestDriver.attackBane(bane: EntityId) {
        removeSummoningSickness(bane)
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(declareAttackers(player1, listOf(bane), player2).error)
    }

    @Test
    fun sevenGenericManaIsRequiredAndBaneIsSevenFive() {
        val d = customScenario(BaneOfBalaGed)
        val player = d.player1
        val bane = d.putCardInHand(player, "Bane of Bala Ged")
        d.giveColorlessMana(player, 6)

        assertNotNull(d.castSpell(player, bane).error)
        assertTrue(d.getHand(player).contains(bane))
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, bane).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(bane))
        assertEquals(7, d.projected.getPower(bane))
        assertEquals(5, d.projected.getToughness(bane))
    }

    @Test
    fun attackingMakesTheDefendingPlayerExileExactlyTwoOfTheirPermanents() {
        val d = customScenario(BaneOfBalaGed, "Sol Ring", "Grizzly Bears", "Phyrexian Arena")
        val bane = d.putCreatureOnBattlefield(d.player1, "Bane of Bala Ged")
        val defender = d.player2
        val ring = d.putPermanentOnBattlefield(defender, "Sol Ring")
        val bear = d.putPermanentOnBattlefield(defender, "Grizzly Bears")
        val arena = d.putPermanentOnBattlefield(defender, "Phyrexian Arena")
        val ownPermanent = d.putPermanentOnBattlefield(d.player1, "Sol Ring")

        d.attackBane(bane)
        d.resolveAll(Choices(pick = listOf("Sol Ring", "Grizzly Bears")))

        assertEquals(setOf(ring, bear), d.getExile(defender).toSet())
        assertEquals(setOf(arena), d.getPermanents(defender).toSet())
        assertTrue(d.getPermanents(d.player1).containsAll(listOf(bane, ownPermanent)))
    }

    @Test
    fun theAbilityDoesNotTriggerWhenBaneOfBalaGedDoesNotAttack() {
        val d = customScenario(BaneOfBalaGed, "Grizzly Bears", "Sol Ring")
        val bane = d.putCreatureOnBattlefield(d.player1, "Bane of Bala Ged")
        val otherAttacker = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val defenderPermanent = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        d.removeSummoningSickness(otherAttacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(otherAttacker), d.player2).error)
        d.resolveAll()

        assertTrue(d.getPermanents(d.player1).contains(bane))
        assertTrue(d.getPermanents(d.player2).contains(defenderPermanent))
        assertTrue(d.getExile(d.player2).isEmpty())
    }
}
