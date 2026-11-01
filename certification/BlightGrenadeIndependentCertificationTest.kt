package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BlightGrenade
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class BlightGrenadeIndependentCertificationTest {
    private val name = "Blight Grenade"

    @Test
    fun destroysItsTargetAndGivesEveryCreatureMinusThreeMinusThree() {
        val d = customScenario(BlightGrenade, "Serra Angel", "Craw Wurm", "Grizzly Bears")
        val target = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val friendlyCreature = d.putCreatureOnBattlefield(d.player1, "Craw Wurm")
        val opposingCreature = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        val creatureReducedToZeroToughness = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK)
        d.giveColorlessMana(d.player1, 4)

        assertNull(d.castSpell(d.player1, spell, listOf(target)).error)
        d.resolveAll()

        assertTrue(d.getGraveyard(d.player2).contains(target))
        assertEquals(3, d.projected.getPower(friendlyCreature))
        assertEquals(1, d.projected.getToughness(friendlyCreature))
        assertEquals(3, d.projected.getPower(opposingCreature))
        assertEquals(1, d.projected.getToughness(opposingCreature))
        assertTrue(d.getGraveyard(d.player2).contains(creatureReducedToZeroToughness))
    }

    @Test
    fun minusThreeMinusThreeExpiresAtTheEndOfTheTurn() {
        val d = customScenario(BlightGrenade, "Serra Angel", "Craw Wurm")
        val target = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val creature = d.putCreatureOnBattlefield(d.player1, "Craw Wurm")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK)
        d.giveColorlessMana(d.player1, 4)

        assertNull(d.castSpell(d.player1, spell, listOf(target)).error)
        d.resolveAll()

        assertEquals(3, d.projected.getPower(creature))
        assertEquals(1, d.projected.getToughness(creature))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertEquals(6, d.projected.getPower(creature))
        assertEquals(4, d.projected.getToughness(creature))
    }

    @Test
    fun requiresACreatureTargetAndRejectsANoncreatureTarget() {
        val noTarget = customScenario(BlightGrenade)
        val uncastSpell = noTarget.putCardInHand(noTarget.player1, name)
        noTarget.giveMana(noTarget.player1, Color.BLACK)
        noTarget.giveColorlessMana(noTarget.player1, 4)

        assertNotNull(noTarget.castSpell(noTarget.player1, uncastSpell).error)
        assertTrue(noTarget.getHand(noTarget.player1).contains(uncastSpell))

        val noncreature = customScenario(BlightGrenade, "Sol Ring")
        val ring = noncreature.putPermanentOnBattlefield(noncreature.player2, "Sol Ring")
        val spell = noncreature.putCardInHand(noncreature.player1, name)
        noncreature.giveMana(noncreature.player1, Color.BLACK)
        noncreature.giveColorlessMana(noncreature.player1, 4)

        assertNotNull(noncreature.castSpell(noncreature.player1, spell, listOf(ring)).error)
        assertTrue(noncreature.getHand(noncreature.player1).contains(spell))
        assertTrue(noncreature.getPermanents(noncreature.player2).contains(ring))
    }

    @Test
    fun fourColorlessManaCannotPayTheBlackManaInItsCost() {
        val d = customScenario(BlightGrenade, "Serra Angel")
        val target = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val spell = d.putCardInHand(d.player1, name)
        d.giveColorlessMana(d.player1, 4)

        assertNotNull(d.castSpell(d.player1, spell, listOf(target)).error)

        assertTrue(d.getHand(d.player1).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(target))
    }

    @Test
    fun threeGenericAndOneBlackManaCannotPayItsCost() {
        val d = customScenario(BlightGrenade, "Serra Angel")
        val target = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK)
        d.giveColorlessMana(d.player1, 3)

        assertNotNull(d.castSpell(d.player1, spell, listOf(target)).error)

        assertTrue(d.getHand(d.player1).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(target))
    }

    @Test
    fun sorceryCannotBeCastDuringTheOpponentsTurn() {
        val d = customScenario(BlightGrenade, "Serra Angel")
        val target = d.putCreatureOnBattlefield(d.player2, "Serra Angel")
        val spell = d.putCardInHand(d.player1, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertNull(d.passPriority(d.player2).error)
        assertEquals(d.player1, d.priorityPlayer)
        d.giveMana(d.player1, Color.BLACK)
        d.giveColorlessMana(d.player1, 4)

        assertNotNull(d.castSpell(d.player1, spell, listOf(target)).error)

        assertTrue(d.getHand(d.player1).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(target))
    }
}
