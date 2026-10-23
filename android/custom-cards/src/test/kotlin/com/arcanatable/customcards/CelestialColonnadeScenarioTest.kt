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

class CelestialColonnadeScenarioTest {
    @Test
    fun entersTappedAndBecomesAFlyingVigilant44() {
        val d = scenario()
        val p = d.player1
        val colonnade = d.putCardInHand(p, "Celestial Colonnade")
        d.playLand(p, colonnade)
        val onBoard = d.findPermanent(p, "Celestial Colonnade")!!
        assertTrue(d.isTapped(onBoard))
        d.giveMana(p, Color.WHITE); d.giveMana(p, Color.BLUE); d.giveColorlessMana(p, 3)
        d.activate(p, onBoard, index = 2)
        d.resolveAll()
        assertTrue(d.projected.isCreature(onBoard))
        assertEquals(4, d.projected.getPower(onBoard))
        assertTrue(d.projected.hasKeyword(onBoard, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(onBoard, Keyword.VIGILANCE))
    }
}
