package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BredForTheHunt
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class BredForTheHuntScenarioTest {
    @Test
    fun eachCounterBearingCreatureThatHitsAPlayerMayDrawOnce() {
        val d = customScenario(BredForTheHunt, "Grizzly Bears")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Bred for the Hunt")
        val bears = List(2) { d.putCreatureOnBattlefield(p, "Grizzly Bears") }
        bears.forEach {
            d.removeSummoningSickness(it)
            d.addComponent(it, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)))
        }
        val before = d.getHand(p).size
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, bears, d.player2).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        // The general turn walker declines optional choices; explicitly accept these draws.
        d.resolveAll(Choices(yes = true))
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertEquals(before + 2, d.getHand(p).size)
    }

    @Test
    fun creaturesWithoutPlusOneCountersDoNotDraw() {
        val d = customScenario(BredForTheHunt, "Grizzly Bears")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Bred for the Hunt")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(bear)
        d.addComponent(bear, CountersComponent(mapOf(CounterType.CHARGE to 1)))
        val before = d.getHand(p).size
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(bear), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()
        assertEquals(before, d.getHand(p).size)
    }

    @Test
    fun controllerCanDeclineTheDraw() {
        val d = customScenario(BredForTheHunt, "Grizzly Bears")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Bred for the Hunt")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(bear)
        d.addComponent(bear, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
        val before = d.getHand(p).size
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(bear), d.player2).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        d.resolveAll(Choices(yes = false))
        assertEquals(before, d.getHand(p).size)
    }
}
