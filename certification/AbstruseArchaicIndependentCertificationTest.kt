package com.arcanatable.customcards

// Independent real-engine certification for Abstruse Archaic, issue #343, candidate #344.
import com.arcanatable.customcards.cards.AbstruseArchaic
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class AbstruseArchaicIndependentCertificationTest {
    @Test fun vigilanceDoesNotTapAttacker() {
        val d = customScenario(AbstruseArchaic)
        val p = d.player1
        val card = d.putCreatureOnBattlefield(p, "Abstruse Archaic")
        d.removeSummoningSickness(card)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(card), d.player2).error)
        assertFalse(d.isTapped(card))
        assertEquals(3, d.projected.getPower(card))
        assertEquals(4, d.projected.getToughness(card))
    }

    @Test fun cannotCopyOpponentsColorlessTrigger() {
        val d = customScenario(AbstruseArchaic, "Ulamog's Crusher")
        val p = d.player1
        val opponent = d.player2
        val archaic = d.putCreatureOnBattlefield(p, "Abstruse Archaic")
        val crusher = d.putCreatureOnBattlefield(opponent, "Ulamog's Crusher")
        d.removeSummoningSickness(archaic)
        d.removeSummoningSickness(crusher)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = opponent)
        assertNull(d.declareAttackers(opponent, listOf(crusher), p).error)
        assertEquals(1, d.stackSize)
        assertNull(d.passPriority(opponent).error)
        d.giveColorlessMana(p, 1)
        val ability = d.cardRegistry.getCard("Abstruse Archaic")!!.script.activatedAbilities.single()
        val trigger = d.getTopOfStack()!!
        assertNotNull(d.submit(ActivateAbility(
            p, archaic, ability.id, targets = listOf(ChosenTarget.Spell(trigger)),
        )).error)
        assertFalse(d.isTapped(archaic))
        assertEquals(1, d.stackSize)
    }

    @Test fun cannotActivateWithoutAbilityOnStack() {
        val d = customScenario(AbstruseArchaic)
        val p = d.player1
        val archaic = d.putCreatureOnBattlefield(p, "Abstruse Archaic")
        d.removeSummoningSickness(archaic)
        d.giveColorlessMana(p, 1)
        assertNotNull(d.activate(p, archaic).error)
        assertFalse(d.isTapped(archaic))
    }
}
