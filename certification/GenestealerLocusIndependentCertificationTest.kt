package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GenestealerLocus
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class GenestealerLocusIndependentCertificationTest {
    @Test
    fun castingForThreeGenericAndBlueManaEntersAsAThreeThreeTyranidHuman() {
        val d = customScenario(GenestealerLocus)
        val player = d.player1
        val locus = d.putCardInHand(player, "Genestealer Locus")
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLUE)

        assertNull(d.castSpell(player, locus).error)
        d.resolveAll()

        val permanent = d.permanentsNamed(player, "Genestealer Locus").single()
        assertTrue(d.projected.hasSubtype(permanent, "TYRANID"))
        assertTrue(d.projected.hasSubtype(permanent, "HUMAN"))
        assertEquals(3, d.projected.getPower(permanent))
        assertEquals(3, d.projected.getToughness(permanent))
    }

    @Test
    fun fourGenericManaWithoutBlueCannotCastGenestealerLocus() {
        val d = customScenario(GenestealerLocus)
        val player = d.player1
        val locus = d.putCardInHand(player, "Genestealer Locus")
        d.giveColorlessMana(player, 4)

        assertNotNull(d.castSpell(player, locus).error)
        assertTrue(d.getHand(player).contains(locus))
        assertTrue(d.permanentsNamed(player, "Genestealer Locus").isEmpty())
    }

    @Test
    fun twoGenericAndBlueManaAreNotEnoughToCastGenestealerLocus() {
        val d = customScenario(GenestealerLocus)
        val player = d.player1
        val locus = d.putCardInHand(player, "Genestealer Locus")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.BLUE)

        assertNotNull(d.castSpell(player, locus).error)
        assertTrue(d.getHand(player).contains(locus))
        assertTrue(d.permanentsNamed(player, "Genestealer Locus").isEmpty())
    }

    @Test
    fun aCreatureAttackingYouGetsMinusOnePowerUntilEndOfTurn() {
        val d = customScenario(GenestealerLocus, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Genestealer Locus")
        val attacker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val bystander = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        d.removeSummoningSickness(bystander)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        assertNull(d.declareAttackers(opponent, listOf(attacker), player).error)
        d.resolveAll()

        assertEquals(1, d.projected.getPower(attacker))
        assertEquals(2, d.projected.getToughness(attacker))
        assertEquals(2, d.projected.getPower(bystander))
        assertEquals(2, d.projected.getToughness(bystander))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)

        assertEquals(2, d.projected.getPower(attacker))
        assertEquals(2, d.projected.getToughness(attacker))
    }

    @Test
    fun aCreatureAttackingAnOpponentGetsPlusOneToughnessUntilEndOfTurn() {
        val d = customScenario(GenestealerLocus, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Genestealer Locus")
        val attacker = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val bystander = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        d.removeSummoningSickness(bystander)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = player)
        assertNull(d.declareAttackers(player, listOf(attacker), opponent).error)
        d.resolveAll()

        assertEquals(2, d.projected.getPower(attacker))
        assertEquals(3, d.projected.getToughness(attacker))
        assertEquals(2, d.projected.getPower(bystander))
        assertEquals(2, d.projected.getToughness(bystander))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = opponent)

        assertEquals(2, d.projected.getPower(attacker))
        assertEquals(2, d.projected.getToughness(attacker))
    }

    @Test
    fun neitherAbilityChangesCreaturesWhenNoAttackOccurs() {
        val d = customScenario(GenestealerLocus, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val locus = d.putCreatureOnBattlefield(player, "Genestealer Locus")
        val friendlyCreature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val opposingCreature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")

        assertEquals(3, d.projected.getPower(locus))
        assertEquals(3, d.projected.getToughness(locus))
        listOf(friendlyCreature, opposingCreature).forEach { creature ->
            assertEquals(2, d.projected.getPower(creature))
            assertEquals(2, d.projected.getToughness(creature))
        }
    }
}
