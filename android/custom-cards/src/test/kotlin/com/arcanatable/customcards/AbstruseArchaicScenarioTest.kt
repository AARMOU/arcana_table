package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AbstruseArchaic
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class AbstruseArchaicScenarioTest {
    @Test
    fun vigilanceKeepsItUntappedWhenItAttacksAndNoAbilityMeansNoCopyTarget() {
        val d = customScenario(AbstruseArchaic)
        val p = d.player1
        val archaic = d.putCreatureOnBattlefield(p, "Abstruse Archaic")
        d.removeSummoningSickness(archaic)
        d.giveColorlessMana(p, 1)

        assertNotNull("There is no activated or triggered ability to target", d.activate(p, archaic).error)
        assertFalse(d.isTapped(archaic))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(archaic), d.player2).error)
        assertFalse("Vigilance leaves Abstruse Archaic untapped after attacking", d.isTapped(archaic))
    }

    @Test
    fun copiesColorlessActivatedAbilityAndMayChooseANewTarget() {
        val d = customScenario(AbstruseArchaic, "Eldrazi Displacer", "Grizzly Bears")
        val p = d.player1
        val archaic = d.putCreatureOnBattlefield(p, "Abstruse Archaic")
        val displacer = d.putCreatureOnBattlefield(p, "Eldrazi Displacer")
        val originalTarget = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val newTarget = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(archaic)
        d.giveColorlessMana(p, 5)
        d.giveMana(p, Color.WHITE)

        assertNull(d.activate(p, displacer).error)
        assertTrue(d.state.pendingDecision is com.wingedsheep.engine.core.ChooseTargetsDecision)
        assertNull(d.submitTargetSelection(p, listOf(originalTarget)).error)
        val originalAbility = d.getTopOfStack()!!

        assertNull(d.activate(p, archaic).error)
        assertTrue(d.state.pendingDecision is com.wingedsheep.engine.core.ChooseTargetsDecision)
        assertNull(d.submitTargetSelection(p, listOf(originalAbility)).error)
        d.resolveAll(Choices(targets = listOf(newTarget)))

        assertTrue("The copied ability resolves on its new target", d.isTapped(newTarget))
        assertTrue("The original ability resolves on its original target", d.isTapped(originalTarget))
        assertTrue(d.isTapped(archaic))
    }

    @Test
    fun copiesColorlessTriggeredAbilityYouControl() {
        val d = customScenario(AbstruseArchaic, "Ulamog's Crusher")
        val p = d.player1
        val opponent = d.player2
        val archaic = d.putCreatureOnBattlefield(p, "Abstruse Archaic")
        val crusher = d.putCreatureOnBattlefield(p, "Ulamog's Crusher")
        repeat(4) { d.putPermanentOnBattlefield(opponent, "Plains") }
        d.removeSummoningSickness(archaic)
        d.removeSummoningSickness(crusher)
        d.giveColorlessMana(p, 1)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(crusher), opponent).error)
        assertEquals("Attacking puts the colorless source's trigger on the stack", 1, d.stackSize)
        val trigger = d.getTopOfStack()!!
        assertNull(d.activate(p, archaic).error)
        assertNull(d.submitTargetSelection(p, listOf(trigger)).error)
        d.resolveAll()

        assertTrue("The original annihilator trigger and its copy each resolve",
            d.getPermanents(opponent).isEmpty())
    }

    @Test
    fun cannotCopyAnAbilityFromAColoredSource() {
        val d = customScenario(AbstruseArchaic, "Mother of Runes", "Grizzly Bears")
        val p = d.player1
        val archaic = d.putCreatureOnBattlefield(p, "Abstruse Archaic")
        val mother = d.putCreatureOnBattlefield(p, "Mother of Runes")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(archaic)
        d.removeSummoningSickness(mother)
        d.giveColorlessMana(p, 1)

        assertNull(d.activate(p, mother).error)
        assertNull(d.submitTargetSelection(p, listOf(bear)).error)
        assertNotNull("A white source does not satisfy the colorless-source restriction",
            d.activate(p, archaic).error)
        assertFalse(d.isTapped(archaic))
        assertEquals(1, d.stackSize)
    }

    @Test
    fun cannotCopyAnAbilityControlledByAnOpponent() {
        val d = customScenario(AbstruseArchaic, "Ulamog's Crusher")
        val p = d.player1
        val opponent = d.player2
        val archaic = d.putCreatureOnBattlefield(p, "Abstruse Archaic")
        val crusher = d.putCreatureOnBattlefield(opponent, "Ulamog's Crusher")
        d.removeSummoningSickness(archaic)
        d.removeSummoningSickness(crusher)
        d.giveColorlessMana(p, 1)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)

        assertNull(d.declareAttackers(opponent, listOf(crusher), p).error)
        assertEquals(1, d.stackSize)
        assertNull(d.passPriority(opponent).error)
        assertNotNull("An opponent's ability is not controlled by Abstruse Archaic",
            d.activate(p, archaic).error)
        assertFalse(d.isTapped(archaic))
    }
}
