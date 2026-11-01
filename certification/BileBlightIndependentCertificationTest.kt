package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BileBlight
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class BileBlightIndependentCertificationTest {
    private val name = "Bile Blight"

    @Test
    fun givesTargetAndEveryCreatureWithTheSameNameMinusThreeMinusThree() {
        val d = customScenario(BileBlight, "Serra Angel", "Giant Spider")
        val target = d.putCreatureOnBattlefield(d.player1, "Serra Angel")
        val sameNameYouControl = d.putCreatureOnBattlefield(d.player1, "Serra Angel")
        val sameNameOpponentControls = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val differentName = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK, 2)

        assertNull(d.castSpell(d.player1, spell, listOf(target)).error)
        d.resolveAll()

        for (creature in listOf(target, sameNameYouControl, sameNameOpponentControls)) {
            assertEquals(1, d.projected.getPower(creature))
            assertEquals(1, d.projected.getToughness(creature))
        }
        assertEquals(2, d.projected.getPower(differentName))
        assertEquals(4, d.projected.getToughness(differentName))
    }

    @Test
    fun creaturesReducedToZeroToughnessDieAndDifferentNamesRemainUnaffected() {
        val d = customScenario(BileBlight, "Grizzly Bears", "Giant Spider")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val sameName = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val differentName = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK, 2)

        assertNull(d.castSpell(d.player1, spell, listOf(target)).error)
        d.resolveAll()

        assertTrue(d.getGraveyard(d.player1).contains(sameName))
        assertTrue(d.getGraveyard(d.player2).contains(target))
        assertTrue(d.getPermanents(d.player2).contains(differentName))
        assertEquals(2, d.projected.getPower(differentName))
        assertEquals(4, d.projected.getToughness(differentName))
    }

    @Test
    fun minusThreeMinusThreeExpiresAtTheEndOfTheTurn() {
        val d = customScenario(BileBlight, "Giant Spider")
        val target = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK, 2)

        assertNull(d.castSpell(d.player1, spell, listOf(target)).error)
        d.resolveAll()

        assertEquals(-1, d.projected.getPower(target))
        assertEquals(1, d.projected.getToughness(target))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertEquals(2, d.projected.getPower(target))
        assertEquals(4, d.projected.getToughness(target))
    }

    @Test
    fun oneBlackAndOneColorlessManaCannotPayTheDoubleBlackCost() {
        val d = customScenario(BileBlight, "Giant Spider")
        val target = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK)
        d.giveColorlessMana(d.player1, 1)

        assertNotNull(d.castSpell(d.player1, spell, listOf(target)).error)

        assertTrue(d.getHand(d.player1).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(target))
        assertEquals(2, d.projected.getPower(target))
        assertEquals(4, d.projected.getToughness(target))
    }

    @Test
    fun instantCanBeCastDuringAnOpponentsTurn() {
        val d = customScenario(BileBlight, "Giant Spider")
        val target = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val spell = d.putCardInHand(d.player1, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.giveMana(d.player1, Color.BLACK, 2)

        assertNull(d.passPriority(d.player2).error)
        assertEquals(d.player1, d.priorityPlayer)
        assertNull(d.castSpell(d.player1, spell, listOf(target)).error)
        d.resolveAll()

        assertEquals(-1, d.projected.getPower(target))
        assertEquals(1, d.projected.getToughness(target))
    }

    @Test
    fun requiresAcreatureTargetAndRejectsANoncreatureTarget() {
        val noTarget = customScenario(BileBlight)
        val uncastSpell = noTarget.putCardInHand(noTarget.player1, name)
        noTarget.giveMana(noTarget.player1, Color.BLACK, 2)

        assertNotNull(noTarget.castSpell(noTarget.player1, uncastSpell).error)
        assertTrue(noTarget.getHand(noTarget.player1).contains(uncastSpell))

        val noncreature = customScenario(BileBlight, "Sol Ring")
        val ring = noncreature.putPermanentOnBattlefield(noncreature.player2, "Sol Ring")
        val spell = noncreature.putCardInHand(noncreature.player1, name)
        noncreature.giveMana(noncreature.player1, Color.BLACK, 2)

        assertNotNull(noncreature.castSpell(noncreature.player1, spell, listOf(ring)).error)
        assertTrue(noncreature.getHand(noncreature.player1).contains(spell))
        assertTrue(noncreature.getPermanents(noncreature.player2).contains(ring))
    }
}
