package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MandateOfAbaddon
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class MandateOfAbaddonIndependentCertificationTest {
    @Test
    fun destroysEveryCreatureWithLessPowerThanTheTarget() {
        val d = customScenario(
            MandateOfAbaddon,
            "Grizzly Bears",
            "Suntail Hawk",
            "Serra Angel",
            "Air Elemental",
            "Colossal Dreadmaw",
        )
        val p = d.player1
        val opponent = d.player2
        val ownSmall = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opposingSmall = d.putCreatureOnBattlefield(opponent, "Suntail Hawk")
        val target = d.putCreatureOnBattlefield(p, "Serra Angel")
        val equalPower = d.putCreatureOnBattlefield(opponent, "Air Elemental")
        val greaterPower = d.putCreatureOnBattlefield(opponent, "Colossal Dreadmaw")
        val spell = d.putCardInHand(p, "Mandate of Abaddon")
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.BLACK)

        assertNull(d.castSpell(p, spell, listOf(target)).error)
        d.resolveAll()

        assertTrue(d.getGraveyard(p).contains(ownSmall))
        assertTrue(d.getGraveyard(opponent).contains(opposingSmall))
        assertTrue(d.getPermanents(p).contains(target))
        assertTrue(d.getPermanents(opponent).contains(equalPower))
        assertTrue(d.getPermanents(opponent).contains(greaterPower))
    }

    @Test
    fun twoGenericManaCannotPayTheThreeGenericManaCost() {
        val d = customScenario(MandateOfAbaddon, "Grizzly Bears")
        val p = d.player1
        val target = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Mandate of Abaddon")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.BLACK)

        assertNotNull(d.castSpell(p, spell, listOf(target)).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(p).contains(target))
    }

    @Test
    fun threeGenericManaCannotPayTheBlackManaCost() {
        val d = customScenario(MandateOfAbaddon, "Grizzly Bears")
        val p = d.player1
        val target = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Mandate of Abaddon")
        d.giveColorlessMana(p, 3)

        assertNotNull(d.castSpell(p, spell, listOf(target)).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(p).contains(target))
    }

    @Test
    fun anOpponentControlledCreatureCannotBeChosen() {
        val d = customScenario(MandateOfAbaddon, "Grizzly Bears")
        val p = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Mandate of Abaddon")
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.BLACK)

        assertNotNull(d.castSpell(p, spell, listOf(target)).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(d.player2).contains(target))
    }

    @Test
    fun aCreatureTargetIsRequired() {
        val d = customScenario(MandateOfAbaddon)
        val p = d.player1
        val spell = d.putCardInHand(p, "Mandate of Abaddon")
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.BLACK)

        assertNotNull(d.castSpell(p, spell).error)
        assertTrue(d.getHand(p).contains(spell))
    }

    @Test
    fun sorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(MandateOfAbaddon, "Grizzly Bears")
        val p = d.player1
        val target = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Mandate of Abaddon")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.BLACK)

        assertNotNull(d.castSpell(p, spell, listOf(target)).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.getPermanents(p).contains(target))
    }
}
