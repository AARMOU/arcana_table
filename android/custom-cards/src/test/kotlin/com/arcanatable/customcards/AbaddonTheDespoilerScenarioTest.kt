package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AbaddonTheDespoiler
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class AbaddonTheDespoilerScenarioTest {
    // CARDKIT-SCAFFOLD: replace this whole test with real scenarios on the engine, e.g.
    //   val d = customScenario(AbaddonTheDespoiler)            // a real game, card round-tripped through its JSON data
    //   val p = d.player1
    //   val card = d.putCardInHand(p, "Abaddon the Despoiler")
    //   d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 2)
    //   assertNull(d.castSpell(p, card).error)
    //   d.resolveAll(Choices(yes = true))
    // Cover, for every line below, the positive case AND a negative/edge case (declined "may",
    // no legal target, intervening-if false, wrong controller, source gone before resolution):
    //   - Trample
    //   - Mark of Chaos Ascendant — During your turn, spells you cast from your hand with mana value X or less have cascade, where X is the total amount of life your opponents have lost this turn.
    @Test
    fun scaffoldIsNotImplementedYet() {
        fail("Abaddon the Despoiler is a cardkit scaffold: implement its abilities and real scenarios first")
    }
}
