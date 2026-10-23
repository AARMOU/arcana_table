package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MageRingNetwork
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class MageRingNetworkScenarioTest {
    @Test
    fun tapsForOneColorlessWithoutCounters() {
        val d = customScenario(MageRingNetwork)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mage-Ring Network")
        assertNull(d.activate(p, land, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun storesCountersThenPaysAChosenNumberForThatMuchColorlessMana() {
        val d = customScenario(MageRingNetwork)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mage-Ring Network")
        d.giveColorlessMana(p, 1)
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll()
        assertEquals(1, d.state.getEntity(land)!!.get<CountersComponent>()!!.getCount(CounterType.STORAGE))

        // The mana-producing ability taps the land; a full turn cycle untaps it normally.
        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        val payOut = d.cardRegistry.getCard("Mage-Ring Network")!!.script.activatedAbilities[2]
        assertNull(d.submit(ActivateAbility(p, land, payOut.id, xValue = 1)).error)
        d.resolveAll()
        assertEquals(0, d.state.getEntity(land)!!.get<CountersComponent>()?.getCount(CounterType.STORAGE) ?: 0)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun mayNotRemoveMoreCountersThanAreAvailable() {
        val d = customScenario(MageRingNetwork)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mage-Ring Network")
        val payOut = d.cardRegistry.getCard("Mage-Ring Network")!!.script.activatedAbilities[2]
        assertNotNull(d.submit(ActivateAbility(p, land, payOut.id, xValue = 2)).error)
        assertFalse(d.isTapped(land))
    }
}
