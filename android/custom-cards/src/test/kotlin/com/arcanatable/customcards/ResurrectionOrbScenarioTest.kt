package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ResurrectionOrb
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class ResurrectionOrbScenarioTest {
    // CARDKIT-SCAFFOLD: replace this whole test with real scenarios on the engine, e.g.
    //   val d = customScenario(ResurrectionOrb)            // a real game, card round-tripped through its JSON data
    //   val p = d.player1
    //   val card = d.putCardInHand(p, "Resurrection Orb")
    //   d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 2)
    //   assertNull(d.castSpell(p, card).error)
    //   d.resolveAll(Choices(yes = true))
    // Cover, for every line below, the positive case AND a negative/edge case (declined "may",
    // no legal target, intervening-if false, wrong controller, source gone before resolution):
    //   - Equipped creature has lifelink.
    //   - Whenever equipped creature dies, return that card to the battlefield under its owner's control at the beginning of the next end step.
    //   - Equip {4}
    @Test
    fun scaffoldIsNotImplementedYet() {
        fail("Resurrection Orb is a cardkit scaffold: implement its abilities and real scenarios first")
    }
}
