package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import org.junit.Assert.assertEquals
import org.junit.Test

class BagEndBanquetScenarioTest {
    @Test
    fun entersWithThreeFoodsAndTapsForColorlessPerFood() {
        val d = scenario()
        val p = d.player1
        val banquet = d.putCardInHand(p, "Bag End Banquet")
        d.giveColorlessMana(p, 6)
        d.castSpell(p, banquet)
        d.resolveStack()
        assertEquals(3, d.permanentsNamed(p, "Food").size)
        d.activate(p, d.findPermanent(p, "Bag End Banquet")!!)
        assertEquals(3, d.manaPool(p).colorless)
    }
}
