package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

/** Regression against upstream Argentum's CommissarSeverinaRaine card, not a duplicate Arcana definition. */
class CommissarSeverinaRaineScenarioTest {
    @Test
    fun attackMakesEachOpponentLoseLifeForOtherAttackers() {
        val d = scenario("Commissar Severina Raine", "Grizzly Bears")
        val p = d.player1
        val commissar = d.putCreatureOnBattlefield(p, "Commissar Severina Raine")
        val bearOne = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val bearTwo = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        listOf(commissar, bearOne, bearTwo).forEach(d::removeSummoningSickness)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = p)
        assertNull(d.declareAttackers(p, listOf(commissar, bearOne, bearTwo), d.player2).error)
        d.resolveAll()

        assertEquals(18, d.getLifeTotal(d.player2))
    }

    @Test
    fun summaryExecutionSacrificesAnotherCreatureAndGainsLifeAndDraws() {
        val d = scenario("Commissar Severina Raine", "Grizzly Bears")
        val p = d.player1
        val commissar = d.putCreatureOnBattlefield(p, "Commissar Severina Raine")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val handSize = d.getHandSize(p)
        val ability = d.cardRegistry.requireCard("Commissar Severina Raine").script.activatedAbilities.single()
        d.giveColorlessMana(p, 2)

        assertNull(d.submit(ActivateAbility(p, commissar, ability.id)).error)
        d.resolveAll()

        assertEquals(22, d.getLifeTotal(p))
        assertEquals(handSize + 1, d.getHandSize(p))
        assertTrue(d.getGraveyard(p).contains(bear))
        assertFalse(d.getGraveyard(p).contains(commissar))
    }
}
