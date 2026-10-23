package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import org.junit.Assert.*
import org.junit.Test

class PowerDepotScenarioTest {
    @Test
    fun entersTappedWithAPlusOneCounter() {
        val d = scenario()
        val p = d.player1
        val depot = d.putCardInHand(p, "Power Depot")
        d.playLand(p, depot)
        d.resolveAll()
        val onBoard = d.findPermanent(p, "Power Depot")!!
        assertTrue(d.isTapped(onBoard))
        assertEquals(1, d.state.getEntity(onBoard)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE))
    }
}
