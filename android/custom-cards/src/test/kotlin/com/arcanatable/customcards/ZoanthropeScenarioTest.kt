package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Zoanthrope
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class ZoanthropeScenarioTest {
    @Test fun castWithXThreeGetsThreeCountersAndDealsThreeToChosenPlayer() {
        val d = customScenario(Zoanthrope)
        val p = d.player1
        val card = d.putCardInHand(p, "Zoanthrope")
        val handBefore = d.getHand(p).size
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)
        assertNull(d.castXSpell(p, card, 3).error)
        d.resolveAll(Choices(targets = listOf(d.player2)))
        assertEquals(3, d.projected.getPower(card))
        assertEquals(3, d.projected.getToughness(card))
        assertEquals(17, d.getLifeTotal(d.player2))
        assertTrue(d.projected.hasKeyword(card, Keyword.FLYING))
        assertEquals(handBefore - 1, d.getHand(p).size)
    }

    @Test fun ravenousFiveDrawsAndWarpBlastUsesFiveDamage() {
        val d = customScenario(Zoanthrope)
        val p = d.player1
        val card = d.putCardInHand(p, "Zoanthrope")
        val handBefore = d.getHand(p).size
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 5)
        assertNull(d.castXSpell(p, card, 5).error)
        d.resolveAll(Choices(targets = listOf(d.player2)))
        assertEquals(5, d.projected.getPower(card))
        assertEquals(5, d.projected.getToughness(card))
        assertEquals(15, d.getLifeTotal(d.player2))
        assertEquals(handBefore, d.getHand(p).size)
    }
}
