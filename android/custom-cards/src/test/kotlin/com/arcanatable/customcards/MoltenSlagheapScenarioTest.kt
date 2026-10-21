package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MoltenSlagheap
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import org.junit.Assert.*
import org.junit.Test

class MoltenSlagheapScenarioTest {
    @Test
    fun depositsStorageThenCashOutUsesChosenXWithoutTapping() {
        val d = customScenario(MoltenSlagheap)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Molten Slagheap")
        d.giveColorlessMana(p, 1)
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll()
        assertTrue(d.isTapped(land))
        assertEquals(1, d.state.getEntity(land)!!.get<CountersComponent>()!!.getCount(CounterType.STORAGE))
        d.giveColorlessMana(p, 1)
        val id = d.cardRegistry.getCard("Molten Slagheap")!!.script.activatedAbilities[2].id
        assertNull(d.submit(ActivateAbility(p, land, id, xValue = 1)).error)
        d.resolveAll()
        assertEquals(0, d.state.getEntity(land)!!.get<CountersComponent>()?.getCount(CounterType.STORAGE) ?: 0)
        assertTrue(d.isTapped(land))
        assertEquals(1, d.manaPool(p).black + d.manaPool(p).red)
    }

    @Test
    fun rejectsMoreStorageCountersThanItHas() {
        val d = customScenario(MoltenSlagheap)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Molten Slagheap")
        d.giveColorlessMana(p, 1)
        val id = d.cardRegistry.getCard("Molten Slagheap")!!.script.activatedAbilities[2].id
        assertNotNull(d.submit(ActivateAbility(p, land, id, xValue = 2)).error)
    }
}
