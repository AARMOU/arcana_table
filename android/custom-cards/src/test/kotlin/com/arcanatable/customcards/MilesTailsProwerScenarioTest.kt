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

class MilesTailsProwerScenarioTest {
    @Test
    fun flyingVehicleDrawsOtherVehiclesGetAFlyingCounter() {
        val d = scenario("Flywheel Racer")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Miles \"Tails\" Prower")
        val hand = d.getHand(p).size
        val copter = d.putCardInHand(p, "Smuggler's Copter")
        d.giveColorlessMana(p, 2)
        d.castSpell(p, copter)
        d.resolveAll()
        assertEquals("cast the Copter, then drew a card", hand + 1, d.getHand(p).size)

        val racer = d.putCardInHand(p, "Flywheel Racer")
        d.giveColorlessMana(p, 2)
        d.castSpell(p, racer)
        d.resolveAll()
        val onBoard = d.findPermanent(p, "Flywheel Racer")!!
        assertEquals(1, d.state.getEntity(onBoard)!!.get<CountersComponent>()!!.getCount(CounterType.FLYING))
    }
}
