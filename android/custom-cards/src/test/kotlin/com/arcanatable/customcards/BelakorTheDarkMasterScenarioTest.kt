package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BelakorTheDarkMaster
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BelakorTheDarkMasterScenarioTest {
    @Test
    fun entryCountsBelakorAndOtherDemonsThenDrawsAndLosesThatMuchLife() {
        val d = customScenario(BelakorTheDarkMaster)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Great Unclean One")
        val belakor = d.putCardInHand(p, "Be'lakor, the Dark Master")
        val handBeforeCast = d.getHandSize(p)
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, belakor).error)
        d.resolveAll()

        assertEquals(18, d.getLifeTotal(p))
        assertEquals(handBeforeCast + 1, d.getHandSize(p))
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun anotherDemonDealsItsPowerToAnyTargetButOtherCreaturesDoNotTrigger() {
        val d = customScenario(BelakorTheDarkMaster, "Grizzly Bears")
        val p = d.player1
        val belakor = d.putCardInHand(p, "Be'lakor, the Dark Master")
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, belakor).error)
        d.resolveAll()
        assertEquals(19, d.getLifeTotal(p))

        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.resolveAll()
        assertEquals(20, d.getLifeTotal(d.player2))
        val enemyCreature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        val demon = d.putCardInHand(p, "Great Unclean One")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, demon).error)
        d.bothPass()
        assertTrue("Demon-entry trigger pauses for target choice", d.pendingDecision is ChooseTargetsDecision)
        assertEquals(20, d.getLifeTotal(d.player2))

        val targetChoice = d.pendingDecision as ChooseTargetsDecision
        assertTrue(targetChoice.legalTargets.values.flatten().contains(enemyCreature))
        d.resolveAll(Choices(targets = listOf(enemyCreature)))
        assertNull(d.findPermanent(d.player2, "Grizzly Bears"))
    }

    @Test
    fun multiplayerEntryCountsOnlyYourDemonsAndCanTargetAThirdPlayer() {
        val d = CardPool.driver()
        val (p1, p2, p3) = d.initMultiplayer(List(3) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p1)
        d.putCreatureOnBattlefield(p1, "Great Unclean One")
        d.putCreatureOnBattlefield(p2, "Great Unclean One")

        val belakor = d.putCardInHand(p1, "Be'lakor, the Dark Master")
        val handBeforeCast = d.getHandSize(p1)
        d.giveMana(p1, Color.BLUE)
        d.giveMana(p1, Color.BLACK)
        d.giveMana(p1, Color.RED)
        d.giveColorlessMana(p1, 3)
        assertNull(d.castSpell(p1, belakor).error)
        d.resolveAll()

        assertEquals(18, d.getLifeTotal(p1))
        assertEquals(handBeforeCast + 1, d.getHandSize(p1))
        assertEquals(20, d.getLifeTotal(p2))
        assertEquals(20, d.getLifeTotal(p3))

        val secondDemon = d.putCardInHand(p1, "Great Unclean One")
        d.giveMana(p1, Color.BLACK)
        d.giveColorlessMana(p1, 4)
        assertNull(d.castSpell(p1, secondDemon).error)
        d.bothPass()
        d.passPriority(p3)
        assertTrue(d.pendingDecision is ChooseTargetsDecision)
        assertEquals(20, d.getLifeTotal(p3))
        val targetChoice = d.pendingDecision as ChooseTargetsDecision
        assertTrue(targetChoice.legalTargets.values.flatten().contains(p3))
        d.resolveAll(Choices(targets = listOf(p3)))
        assertEquals(16, d.getLifeTotal(p3))
    }
}
