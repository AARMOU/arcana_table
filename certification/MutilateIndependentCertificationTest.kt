package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Mutilate
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class MutilateIndependentCertificationTest {
    private val name = "Mutilate"

    @Test
    fun oneSwampGivesEveryCreatureMinusOneMinusOne() {
        val d = customScenario(Mutilate, "Grizzly Bears", "Giant Spider")
        val ownCreature = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val opposingCreature = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        d.putLandOnBattlefield(d.player1, "Swamp")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 2)

        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()

        assertEquals(1, d.projected.getPower(ownCreature))
        assertEquals(1, d.projected.getToughness(ownCreature))
        assertEquals(1, d.projected.getPower(opposingCreature))
        assertEquals(3, d.projected.getToughness(opposingCreature))
    }

    @Test
    fun eachSwampYouControlAddsAnotherMinusOneMinusOneButOpponentsSwampDoesNotCount() {
        val d = customScenario(Mutilate, "Grizzly Bears", "Giant Spider")
        val ownCreature = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val opposingCreature = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        d.putLandOnBattlefield(d.player1, "Swamp")
        d.putLandOnBattlefield(d.player1, "Swamp")
        d.putLandOnBattlefield(d.player2, "Swamp")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 2)

        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()

        assertTrue(d.getGraveyard(d.player1).contains(ownCreature))
        assertEquals(0, d.projected.getPower(opposingCreature))
        assertEquals(2, d.projected.getToughness(opposingCreature))
    }

    @Test
    fun anOpponentControlledSwampDoesNotGiveTheSpellAnyReduction() {
        val d = customScenario(Mutilate, "Grizzly Bears", "Giant Spider")
        val ownCreature = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val opposingCreature = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        d.putLandOnBattlefield(d.player2, "Swamp")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 2)

        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()

        assertEquals(2, d.projected.getPower(ownCreature))
        assertEquals(2, d.projected.getToughness(ownCreature))
        assertEquals(2, d.projected.getPower(opposingCreature))
        assertEquals(4, d.projected.getToughness(opposingCreature))
    }

    @Test
    fun creaturesWithZeroToughnessDieAndZeroPowerAloneDoesNotKill() {
        val d = customScenario(Mutilate, "Grizzly Bears", "Giant Spider")
        val ownBear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val opposingBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val opposingSpider = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        d.putLandOnBattlefield(d.player1, "Swamp")
        d.putLandOnBattlefield(d.player1, "Swamp")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 2)

        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()

        assertTrue(d.getGraveyard(d.player1).contains(ownBear))
        assertTrue(d.getGraveyard(d.player2).contains(opposingBear))
        assertTrue(d.getPermanents(d.player2).contains(opposingSpider))
        assertEquals(0, d.projected.getPower(opposingSpider))
        assertEquals(2, d.projected.getToughness(opposingSpider))
    }

    @Test
    fun theMinusOneMinusOneEffectExpiresAtTheEndOfTheTurn() {
        val d = customScenario(Mutilate, "Giant Spider")
        val spider = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        d.putLandOnBattlefield(d.player1, "Swamp")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 2)

        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()

        assertEquals(1, d.projected.getPower(spider))
        assertEquals(3, d.projected.getToughness(spider))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertEquals(2, d.projected.getPower(spider))
        assertEquals(4, d.projected.getToughness(spider))
    }

    @Test
    fun twoBlackManaAndTwoGenericManaAreRequiredToCast() {
        val d = customScenario(Mutilate, "Giant Spider")
        val spider = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val spell = d.putCardInHand(d.player1, name)
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 1)

        assertNotNull(d.castSpell(d.player1, spell).error)
        assertTrue(d.getHand(d.player1).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(spider))

        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()
        assertEquals(2, d.projected.getPower(spider))
        assertEquals(4, d.projected.getToughness(spider))
    }

    @Test
    fun aSorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(Mutilate, "Giant Spider")
        val spider = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        val spell = d.putCardInHand(d.player1, name)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.passPriority(d.player2).error)
        assertEquals(d.player1, d.priorityPlayer)

        assertNotNull(d.castSpell(d.player1, spell).error)
        assertTrue(d.getHand(d.player1).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(spider))
        assertEquals(2, d.projected.getPower(spider))
        assertEquals(4, d.projected.getToughness(spider))
    }
}
