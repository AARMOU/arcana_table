package com.arcanatable.customcards

import com.arcanatable.customcards.cards.StarOfExtinction
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class StarOfExtinctionScenarioTest {
    // CARDKIT-SCAFFOLD: replace this whole test with real scenarios on the engine, e.g.
    //   val d = customScenario(StarOfExtinction)            // a real game, card round-tripped through its JSON data
    //   val p = d.player1
    //   val card = d.putCardInHand(p, "Star of Extinction")
    //   d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 2)
    //   assertNull(d.castSpell(p, card).error)
    //   d.resolveAll(Choices(yes = true))
    // Cover, for every line below, the positive case AND a negative/edge case (declined "may",
    // no legal target, intervening-if false, wrong controller, source gone before resolution):
    //   - Destroy target land. Star of Extinction deals 20 damage to each creature and each planeswalker.
    @Test
    fun scaffoldIsNotImplementedYet() {
        fail("Star of Extinction is a cardkit scaffold: implement its abilities and real scenarios first")
    }
}
