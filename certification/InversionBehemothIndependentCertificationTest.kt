package com.arcanatable.customcards

import com.arcanatable.customcards.cards.InversionBehemoth
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class InversionBehemothIndependentCertificationTest {
    private val name = "Inversion Behemoth"

    @Test
    fun castingCostsTwoGenericAndTwoColorlessAndEntersAsATwoNine() {
        val d = customScenario(InversionBehemoth)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.BLUE, 2)

        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(card))
        assertEquals(2, d.projected.getPower(card))
        assertEquals(9, d.projected.getToughness(card))
        assertEquals(0, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun coloredManaCannotPayTheTwoColorlessSymbols() {
        val d = customScenario(InversionBehemoth)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveMana(p, Color.BLUE, 4)

        assertNotNull(d.castSpell(p, card).error)

        assertTrue(d.getHand(p).contains(card))
        assertTrue(d.permanentsNamed(p, name).isEmpty())
        assertEquals(4, d.manaPool(p).blue)
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun beginningCombatOnYourTurnSwitchesEachChosenCreatureUntilEndOfTurn() {
        val d = customScenario(InversionBehemoth, "Giant Spider", "Llanowar Elves")
        val p = d.player1
        val opponent = d.player2
        val behemoth = d.putCreatureOnBattlefield(p, name)
        val ownSpider = d.putCreatureOnBattlefield(p, "Giant Spider")
        val opposingSpider = d.putCreatureOnBattlefield(opponent, "Giant Spider")
        val unchosenElf = d.putCreatureOnBattlefield(p, "Llanowar Elves")

        assertEquals(2, d.projected.getPower(ownSpider))
        assertEquals(4, d.projected.getToughness(ownSpider))
        d.passPriorityUntil(Step.BEGINNING_COMBAT, activePlayer = p)
        assertEquals(2, d.projected.getPower(ownSpider))

        d.resolveAll(Choices(targets = listOf(ownSpider, opposingSpider)))

        for (spider in listOf(ownSpider, opposingSpider)) {
            assertEquals(4, d.projected.getPower(spider))
            assertEquals(2, d.projected.getToughness(spider))
        }
        assertEquals(p, d.projected.getController(ownSpider))
        assertEquals(opponent, d.projected.getController(opposingSpider))
        assertEquals(1, d.projected.getPower(unchosenElf))
        assertEquals(1, d.projected.getToughness(unchosenElf))

        d.passPriorityUntil(Step.END, activePlayer = p)
        assertEquals(4, d.projected.getPower(ownSpider))
        assertEquals(2, d.projected.getToughness(ownSpider))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)

        assertEquals(2, d.projected.getPower(ownSpider))
        assertEquals(4, d.projected.getToughness(ownSpider))
        assertTrue(d.permanentsNamed(p, name).contains(behemoth))
    }

    @Test
    fun theAnyNumberTargetingAllowsChoosingNoCreatures() {
        val d = customScenario(InversionBehemoth, "Giant Spider")
        val p = d.player1
        d.putCreatureOnBattlefield(p, name)
        val spider = d.putCreatureOnBattlefield(p, "Giant Spider")

        d.passPriorityUntil(Step.BEGINNING_COMBAT, activePlayer = p)
        d.resolveAll(Choices(targets = emptyList()))

        assertEquals(2, d.projected.getPower(spider))
        assertEquals(4, d.projected.getToughness(spider))
    }

    @Test
    fun aNoncreaturePermanentIsNotOfferedOrAcceptedAsATarget() {
        val d = customScenario(InversionBehemoth, "Giant Spider", "Sol Ring")
        val p = d.player1
        d.putCreatureOnBattlefield(p, name)
        val spider = d.putCreatureOnBattlefield(p, "Giant Spider")
        val ring = d.putPermanentOnBattlefield(d.player2, "Sol Ring")

        d.passPriorityUntil(Step.BEGINNING_COMBAT, activePlayer = p)
        val decision = d.state.pendingDecision as? ChooseTargetsDecision
        assertNotNull("the trigger must choose its targets", decision)
        val legalTargets = decision!!.legalTargets.values.flatten()
        assertTrue(legalTargets.contains(spider))
        assertFalse(legalTargets.contains(ring))
        assertNotNull(d.submitTargetSelection(p, listOf(ring)).error)
        assertNull(d.submitTargetSelection(p, listOf(spider)).error)
        d.resolveAll()

        assertEquals(4, d.projected.getPower(spider))
        assertEquals(2, d.projected.getToughness(spider))
        assertTrue(d.permanentsNamed(d.player2, "Sol Ring").contains(ring))
    }

    @Test
    fun theTriggerDoesNotOccurDuringAnOpponentsCombat() {
        val d = customScenario(InversionBehemoth, "Giant Spider")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, name)
        val spider = d.putCreatureOnBattlefield(p, "Giant Spider")

        d.passPriorityUntil(Step.BEGINNING_COMBAT, activePlayer = opponent)

        assertTrue(d.state.stack.isEmpty())
        assertEquals(2, d.projected.getPower(spider))
        assertEquals(4, d.projected.getToughness(spider))
    }
}
