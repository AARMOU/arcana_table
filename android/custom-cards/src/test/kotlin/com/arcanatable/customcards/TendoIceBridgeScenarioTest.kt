package com.arcanatable.customcards
import com.arcanatable.customcards.cards.TendoIceBridge
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import org.junit.Assert.*
import org.junit.Test
class TendoIceBridgeScenarioTest {
    @Test fun chargeCounterPaysForOneColoredManaUse() {
        val d = customScenario(TendoIceBridge)
        val p = d.player1
        val land = d.putCardInHand(p, "Tendo Ice Bridge")
        assertNull(d.playLand(p, land).error)
        d.resolveAll()
        val bridge = d.findPermanent(p, "Tendo Ice Bridge")!!
        assertEquals(1, d.state.getEntity(bridge)!!.get<CountersComponent>()!!.getCount(CounterType.CHARGE))
        assertNull(d.activate(p, bridge, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))
        assertEquals(0, d.state.getEntity(bridge)!!.get<CountersComponent>()?.getCount(CounterType.CHARGE) ?: 0)
        assertEquals(1, d.manaPool(p).green)
    }
}
