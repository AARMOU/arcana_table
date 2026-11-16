package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PlagueDrone
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class PlagueDroneIndependentCertificationTest {
    @Test
    fun castingRequiresThreeGenericAndOneBlackManaAndEntersAsAThreeThreeDemon() {
        val d = customScenario(PlagueDrone)
        val player = d.player1
        val card = d.putCardInHand(player, "Plague Drone")
        d.giveColorlessMana(player, 4)

        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.getHand(player).contains(card))
        assertTrue(d.permanentsNamed(player, "Plague Drone").isEmpty())

        d.giveMana(player, Color.BLACK)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Plague Drone").contains(card))
        assertTrue(d.projected.isCreature(card))
        assertTrue(d.projected.hasSubtype(card, "DEMON"))
        assertEquals(3, d.projected.getPower(card))
        assertEquals(3, d.projected.getToughness(card))
    }

    @Test
    fun flyingPreventsAGroundCreatureFromBlockingAndPlagueDroneDealsThreeCombatDamage() {
        val d = customScenario(PlagueDrone, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val drone = d.putCreatureOnBattlefield(player, "Plague Drone")
        val bear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(drone)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertTrue(d.projected.hasKeyword(drone, Keyword.FLYING))
        assertNull(d.declareAttackers(player, listOf(drone), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(opponent, mapOf(bear to listOf(drone))).error)
        assertNull(d.declareNoBlockers(opponent).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(17, d.getLifeTotal(opponent))
        assertTrue(d.permanentsNamed(player, "Plague Drone").contains(drone))
        assertTrue(d.permanentsNamed(opponent, "Grizzly Bears").contains(bear))
    }

    @Test
    fun anOpponentsFourLifeGainBecomesFourLifeLoss() {
        val d = customScenario(PlagueDrone, "Lone Missionary")
        val player = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(player, "Plague Drone")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val missionary = d.putCardInHand(opponent, "Lone Missionary")
        d.giveMana(opponent, Color.WHITE)
        d.giveColorlessMana(opponent, 1)

        assertNull(d.castSpell(opponent, missionary).error)
        d.resolveAll()

        assertEquals(16, d.getLifeTotal(opponent))
        assertEquals(20, d.getLifeTotal(player))
        assertTrue(d.permanentsNamed(opponent, "Lone Missionary").contains(missionary))
    }

    @Test
    fun plagueDroneDoesNotReplaceItsControllersLifeGain() {
        val d = customScenario(PlagueDrone, "Lone Missionary")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Plague Drone")
        val missionary = d.putCardInHand(player, "Lone Missionary")
        d.giveMana(player, Color.WHITE)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpell(player, missionary).error)
        d.resolveAll()

        assertEquals(24, d.getLifeTotal(player))
        assertEquals(20, d.getLifeTotal(d.player2))
        assertTrue(d.permanentsNamed(player, "Lone Missionary").contains(missionary))
    }
}
