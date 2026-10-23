package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UginsInsight
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class UginsInsightScenarioTest {
    @Test
    fun zeroManaValueBoardDoesNotScryButStillDrawsThree() {
        val d = customScenario(UginsInsight)
        val p = d.player1
        val card = d.putCardInHand(p, "Ugin's Insight")
        val before = d.getHand(p).size
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.BLUE, 2)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertEquals(before + 2, d.getHand(p).size)
    }

    @Test
    fun nonzeroManaValueBoardScriesThenDrawsThree() {
        val d = customScenario(UginsInsight, "Sol Ring")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Sol Ring")
        val card = d.putCardInHand(p, "Ugin's Insight")
        val before = d.getHand(p).size
        d.giveColorlessMana(p, 3)
        d.giveMana(p, Color.BLUE, 2)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertEquals(before + 2, d.getHand(p).size)
    }
}
