package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KnucklesTheEchidna
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class KnucklesTheEchidnaScenarioTest {
    // CARDKIT-SCAFFOLD: replace this whole test with real scenarios on the engine, e.g.
    //   val d = customScenario(KnucklesTheEchidna)            // a real game, card round-tripped through its JSON data
    //   val p = d.player1
    //   val card = d.putCardInHand(p, "Knuckles the Echidna")
    //   d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 2)
    //   assertNull(d.castSpell(p, card).error)
    //   d.resolveAll(Choices(yes = true))
    // Cover, for every line below, the positive case AND a negative/edge case (declined "may",
    // no legal target, intervening-if false, wrong controller, source gone before resolution):
    //   - Double strike, trample, haste
    //   - Whenever one or more creatures you control deal combat damage to a player, create a Treasure token.
    //   - Treasure Hunter — At the beginning of your upkeep, if you control thirty or more artifacts, you win the game.
    @Test
    fun scaffoldIsNotImplementedYet() {
        fail("Knuckles the Echidna is a cardkit scaffold: implement its abilities and real scenarios first")
    }
}
