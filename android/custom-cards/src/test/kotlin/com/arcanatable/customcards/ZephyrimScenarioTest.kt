package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Zephyrim
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class ZephyrimScenarioTest {
    // CARDKIT-SCAFFOLD: replace this whole test with real scenarios on the engine, e.g.
    //   val d = customScenario(Zephyrim)            // a real game, card round-tripped through its JSON data
    //   val p = d.player1
    //   val card = d.putCardInHand(p, "Zephyrim")
    //   d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 2)
    //   assertNull(d.castSpell(p, card).error)
    //   d.resolveAll(Choices(yes = true))
    // Cover, for every line below, the positive case AND a negative/edge case (declined "may",
    // no legal target, intervening-if false, wrong controller, source gone before resolution):
    //   - Squad {2}
    //   - Flying, vigilance
    //   - Miracle {1}{W}
    @Test
    fun scaffoldIsNotImplementedYet() {
        fail("Zephyrim is a cardkit scaffold: implement its abilities and real scenarios first")
    }
}
