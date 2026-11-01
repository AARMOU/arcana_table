package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ArchOfOrazca
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class ArchOfOrazcaScenarioTest {
    // CARDKIT-SCAFFOLD: replace this whole test with real scenarios on the engine, e.g.
    //   val d = customScenario(ArchOfOrazca)            // a real game, card round-tripped through its JSON data
    //   val p = d.player1
    //   val card = d.putCardInHand(p, "Arch of Orazca")
    //   d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 2)
    //   assertNull(d.castSpell(p, card).error)
    //   d.resolveAll(Choices(yes = true))
    // Cover, for every line below, the positive case AND a negative/edge case (declined "may",
    // no legal target, intervening-if false, wrong controller, source gone before resolution):
    //   - Ascend
    //   - {T}: Add {C}.
    //   - {5}, {T}: Draw a card. Activate only if you have the city's blessing.
    @Test
    fun scaffoldIsNotImplementedYet() {
        fail("Arch of Orazca is a cardkit scaffold: implement its abilities and real scenarios first")
    }
}
