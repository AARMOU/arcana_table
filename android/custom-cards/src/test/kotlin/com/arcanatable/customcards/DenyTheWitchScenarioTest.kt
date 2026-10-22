package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DenyTheWitch
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class DenyTheWitchScenarioTest {
    @Test fun countersOpponentsCreatureSpellAndChargesForCurrentCreatureCount() {
        val d = customScenario(DenyTheWitch, "Grizzly Bears", "Llanowar Elves")
        val p = d.player1
        val opp = d.player2
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Llanowar Elves")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opp)
        val targetCard = d.putCardInHand(opp, "Grizzly Bears")
        d.giveMana(opp, Color.GREEN, 2)
        assertNull(d.castSpell(opp, targetCard).error)
        val onStack = d.getTopOfStack()!!
        assertNull(d.passPriority(opp).error)

        val deny = d.putCardInHand(p, "Deny the Witch")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)
        assertNull(d.submit(CastSpell(
            playerId = p, cardId = deny,
            targets = listOf(ChosenTarget.Spell(onStack)),
            paymentStrategy = PaymentStrategy.FromPool
        )).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(opp, "Grizzly Bears").isEmpty())
        assertTrue(d.zoneNames(opp, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertEquals(18, d.getLifeTotal(opp))
    }

    @Test fun controllerLosesNoLifeIfYouControlNoCreatures() {
        val d = customScenario(DenyTheWitch, "Grizzly Bears")
        val p = d.player1
        val opp = d.player2
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opp)
        val target = d.putCardInHand(opp, "Grizzly Bears")
        d.giveMana(opp, Color.GREEN, 2)
        assertNull(d.castSpell(opp, target).error)
        val onStack = d.getTopOfStack()!!
        assertNull(d.passPriority(opp).error)

        val deny = d.putCardInHand(p, "Deny the Witch")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)
        assertNull(d.submit(CastSpell(
            playerId = p, cardId = deny,
            targets = listOf(ChosenTarget.Spell(onStack)),
            paymentStrategy = PaymentStrategy.FromPool
        )).error)
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(opp))
        assertTrue(d.zoneNames(opp, Zone.GRAVEYARD).contains("Grizzly Bears"))
    }
}
