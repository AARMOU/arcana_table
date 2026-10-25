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

/** Regression against upstream Argentum's TataruTaru card, not a duplicate Arcana definition. */
class TataruTaruScenarioTest {
    @Test
    fun entersDrawingForBothAndOpponentsOffTurnDrawMakesATappedTreasure() {
        val d = scenario("Tataru Taru")
        val p = d.player1
        val opp = d.player2
        val handBefore = d.getHand(p).size
        val oppBefore = d.getHand(opp).size
        val tataru = d.putCardInHand(p, "Tataru Taru")
        d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 1)
        d.castSpell(p, tataru)
        d.resolveAll(Choices(yes = true, targets = listOf(opp)))
        assertEquals(handBefore + 1, d.getHand(p).size)
        assertEquals(oppBefore + 1, d.getHand(opp).size)
        val treasures = d.permanentsNamed(p, "Treasure")
        assertEquals(1, treasures.size)
        assertTrue(d.isTapped(treasures.single()))
    }
}
