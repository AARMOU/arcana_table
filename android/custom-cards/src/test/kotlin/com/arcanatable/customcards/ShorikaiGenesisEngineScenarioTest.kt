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

class ShorikaiGenesisEngineScenarioTest {
    @Test
    fun drawsTwoDiscardsOneAndMakesAPilot() {
        val d = scenario()
        val p = d.player1
        val shorikai = d.putPermanentOnBattlefield(p, "Shorikai, Genesis Engine")
        val hand = d.getHand(p).size
        d.giveColorlessMana(p, 1)
        d.activate(p, shorikai)
        d.resolveAll()
        assertEquals(hand + 1, d.getHand(p).size)
        assertEquals(1, d.permanentsNamed(p, "Pilot Token").size)
    }
}
