package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MonstrousOnslaught
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class MonstrousOnslaughtScenarioTest {
    // CARDKIT-SCAFFOLD: replace this whole test with real scenarios on the engine, e.g.
    //   val d = customScenario(MonstrousOnslaught)            // a real game, card round-tripped through its JSON data
    //   val p = d.player1
    //   val card = d.putCardInHand(p, "Monstrous Onslaught")
    //   d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 2)
    //   assertNull(d.castSpell(p, card).error)
    //   d.resolveAll(Choices(yes = true))
    // Cover, for every line below, the positive case AND a negative/edge case (declined "may",
    // no legal target, intervening-if false, wrong controller, source gone before resolution):
    //   - Monstrous Onslaught deals X damage divided as you choose among any number of target creatures, where X is the greatest power among creatures you control as you cast this spell.
    @Test
    fun scaffoldIsNotImplementedYet() {
        fail("Monstrous Onslaught is a cardkit scaffold: implement its abilities and real scenarios first")
    }
}
