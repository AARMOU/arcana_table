package com.arcanatable.customcards

import com.arcanatable.customcards.cards.VerdantSunsAvatar
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class VerdantSunsAvatarScenarioTest {
    // CARDKIT-SCAFFOLD: replace this whole test with real scenarios on the engine, e.g.
    //   val d = customScenario(VerdantSunsAvatar)            // a real game, card round-tripped through its JSON data
    //   val p = d.player1
    //   val card = d.putCardInHand(p, "Verdant Sun's Avatar")
    //   d.giveMana(p, Color.WHITE); d.giveColorlessMana(p, 2)
    //   assertNull(d.castSpell(p, card).error)
    //   d.resolveAll(Choices(yes = true))
    // Cover, for every line below, the positive case AND a negative/edge case (declined "may",
    // no legal target, intervening-if false, wrong controller, source gone before resolution):
    //   - Whenever this creature or another creature you control enters, you gain life equal to that creature's toughness.
    @Test
    fun scaffoldIsNotImplementedYet() {
        fail("Verdant Sun's Avatar is a cardkit scaffold: implement its abilities and real scenarios first")
    }
}
