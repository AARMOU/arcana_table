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

class TheFantasticarScenarioTest {
    @Test
    fun animatesOnNoncreatureSpellsAndFourthMakesConstructs() {
        val d = scenario("Dark Ritual")
        val p = d.player1
        val car = d.putPermanentOnBattlefield(p, "The Fantasticar")
        val rituals = List(4) { d.putCardInHand(p, "Dark Ritual") }
        d.giveMana(p, Color.BLACK)
        d.castSpell(p, rituals[0])
        d.resolveAll(Choices(yes = true))
        assertTrue(d.projected.isCreature(car))
        for (r in rituals.drop(1)) { d.castSpell(p, r); d.resolveAll(Choices(yes = true)) }
        assertNull(d.findPermanent(p, "The Fantasticar"))
        assertEquals(4, d.getPermanents(p).count { d.getCardName(it) == "Construct Token" })
    }
}
