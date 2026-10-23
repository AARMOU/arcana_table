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

class LotusFieldScenarioTest {
    @Test
    fun sacrificesTwoUntappedLandsAndTapsForThree() {
        val d = scenario()
        val p = d.player1
        repeat(2) { d.putLandOnBattlefield(p, "Plains") }
        val field = d.putCardInHand(p, "Lotus Field")
        d.playLand(p, field)
        d.resolveAll(Choices(pick = listOf("Plains")))
        assertEquals(0, d.permanentsNamed(p, "Plains").size)
        val onBoard = d.findPermanent(p, "Lotus Field")!!
        assertTrue(d.isTapped(onBoard))
        assertTrue(d.projected.hasKeyword(onBoard, Keyword.HEXPROOF))
    }
}
