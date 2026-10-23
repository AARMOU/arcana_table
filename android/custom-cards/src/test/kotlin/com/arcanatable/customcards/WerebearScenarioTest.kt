package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Werebear
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class WerebearScenarioTest {
    @Test
    fun tapsForGreenAndGetsThresholdBonusAtSevenGraveyardCards() {
        val belowThreshold = scenario()
        belowThreshold.registerCard(Werebear)
        repeat(6) { belowThreshold.putCardInGraveyard(belowThreshold.player1, "Plains") }
        val smallBear = belowThreshold.putPermanentOnBattlefield(belowThreshold.player1, "Werebear")
        assertEquals(1, belowThreshold.projected.getPower(smallBear))

        // Build the threshold state before projection so the assertion measures the live static effect.
        val d = scenario()
        d.registerCard(Werebear)
        val p = d.player1
        repeat(7) { d.putCardInGraveyard(p, "Plains") }
        val bear = d.putPermanentOnBattlefield(p, "Werebear")
        assertEquals(4, d.projected.getPower(bear))
        assertEquals(4, d.projected.getToughness(bear))

        d.activate(p, bear)
        assertEquals(1, d.manaPool(p).green)
    }
}
