package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HeraldOfSlaanesh
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class HeraldOfSlaaneshIndependentCertificationTest {
    @Test
    fun demonSpellYouCastCostsTwoGenericManaLess() {
        val d = customScenario(HeraldOfSlaanesh)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Herald of Slaanesh")
        val spell = d.putCardInHand(p, "Herald of Slaanesh")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()

        assertEquals(2, d.permanentsNamed(p, "Herald of Slaanesh").size)
        assertEquals(0, d.manaPool(p).red)
        assertEquals(2, d.manaPool(p).colorless)
    }

    @Test
    fun demonSpellStillRequiresItsColoredMana() {
        val d = customScenario(HeraldOfSlaanesh)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Herald of Slaanesh")
        val spell = d.putCardInHand(p, "Herald of Slaanesh")
        d.giveColorlessMana(p, 2)

        assertNotNull(d.castSpell(p, spell).error)
        assertTrue(d.getHand(p).contains(spell))
        assertEquals(2, d.manaPool(p).colorless)
        assertEquals(0, d.manaPool(p).red)
    }

    @Test
    fun nonDemonSpellDoesNotGetTheCostReduction() {
        val d = customScenario(HeraldOfSlaanesh, "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Herald of Slaanesh")
        val spell = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)

        assertNotNull(d.castSpell(p, spell).error)
        assertTrue(d.getHand(p).contains(spell))
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertEquals(1, d.manaPool(p).green)
    }

    @Test
    fun opponentMustPayTheFullCostForTheirDemonSpell() {
        val d = customScenario(HeraldOfSlaanesh)
        val opponent = d.player2
        d.putCreatureOnBattlefield(d.player1, "Herald of Slaanesh")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val spell = d.putCardInHand(opponent, "Herald of Slaanesh")
        d.giveMana(opponent, Color.RED)

        assertNotNull(d.castSpell(opponent, spell).error)
        assertTrue(d.getHand(opponent).contains(spell))

        d.giveColorlessMana(opponent, 2)
        assertNull(d.castSpell(opponent, spell).error)
        d.resolveAll()

        assertEquals(1, d.permanentsNamed(opponent, "Herald of Slaanesh").size)
        assertEquals(0, d.manaPool(opponent).red)
        assertEquals(0, d.manaPool(opponent).colorless)
    }

    @Test
    fun aHeraldAndANonDemonDoNotGainHasteFromThatHerald() {
        val d = customScenario(HeraldOfSlaanesh, "Grizzly Bears")
        val p = d.player1
        val herald = d.putCreatureOnBattlefield(p, "Herald of Slaanesh")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNotNull(d.declareAttackers(p, listOf(herald), d.player2).error)
        assertNotNull(d.declareAttackers(p, listOf(bear), d.player2).error)
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun anotherDemonYouControlCanAttackTheTurnItEnters() {
        val d = customScenario(HeraldOfSlaanesh)
        val p = d.player1
        val attacker = d.putCreatureOnBattlefield(p, "Herald of Slaanesh")
        d.putCreatureOnBattlefield(p, "Herald of Slaanesh")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(attacker), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(18, d.getLifeTotal(d.player2))
    }

    @Test
    fun heraldDoesNotGiveHasteToAnOpponentsDemon() {
        val d = customScenario(HeraldOfSlaanesh)
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Herald of Slaanesh")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val opposingDemon = d.putCreatureOnBattlefield(opponent, "Herald of Slaanesh")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)

        assertNotNull(d.declareAttackers(opponent, listOf(opposingDemon), p).error)
        assertEquals(20, d.getLifeTotal(p))
    }
}
