package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AbstruseArchaic
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
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
        val d = customScenario(AbstruseArchaic, "Grizzly Bears", "Murder")
        val p = d.player1
        val opponent = d.player2
        val archaic = d.putCreatureOnBattlefield(p, "Abstruse Archaic")
        val hollow = d.putPermanentOnBattlefield(p, "Yavimaya Hollow")
        val originalTarget = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val newTarget = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val regenerate = d.cardRegistry.getCard("Yavimaya Hollow")!!.script.activatedAbilities[1]
        val murder = d.putCardInHand(opponent, "Murder")
        d.removeSummoningSickness(archaic)
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.GREEN)

        assertNull(d.submit(ActivateAbility(
            p, hollow, regenerate.id, targets = listOf(ChosenTarget.Permanent(originalTarget)),
        )).error)
        val originalAbility = d.getTopOfStack()!!

        assertNull(d.activate(p, archaic).error)
        assertTrue(d.state.pendingDecision is com.wingedsheep.engine.core.ChooseTargetsDecision)
        assertNull(d.submitTargetSelection(p, listOf(originalAbility)).error)
        d.resolveAll(Choices(targets = listOf(newTarget)))

        d.passPriority(p)
        d.giveMana(opponent, Color.BLACK, 3)
        assertNull(d.castSpell(opponent, murder, listOf(newTarget)).error)
        d.resolveAll()

        assertTrue("The copied ability regenerates its new target", newTarget in d.getPermanents(p))
        assertTrue("The regenerated target is tapped", d.isTapped(newTarget))
        assertTrue("The original ability resolves on its original target", originalTarget in d.getPermanents(p))
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
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(p, listOf(crusher), opponent).error)
        assertEquals("Attacking puts the colorless source's trigger on the stack", 1, d.stackSize)
        val trigger = d.getTopOfStack()!!
        d.giveColorlessMana(p, 1)
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
        val protection = d.cardRegistry.getCard("Mother of Runes")!!.script.activatedAbilities.single()

        assertNull(d.submit(ActivateAbility(
            p, mother, protection.id, targets = listOf(ChosenTarget.Permanent(bear)),
        )).error)
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
        d.removeSummoningSickness(crusher)
        d.removeSummoningSickness(archaic)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)

        assertNull(d.declareAttackers(opponent, listOf(crusher), p).error)
        assertEquals(1, d.stackSize)
        assertNull(d.passPriority(opponent).error)
        d.giveColorlessMana(p, 1)
        assertNotNull("An opponent's ability is not controlled by Abstruse Archaic",
            d.activate(p, archaic).error)
        assertFalse(d.isTapped(archaic))
    }
}
