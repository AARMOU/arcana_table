package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TriarchPraetorian
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class TriarchPraetorianScenarioTest {
    @Test fun normalHandCastDoesNotDrawOrLoseLife() {
        val d = customScenario(TriarchPraetorian)
        val p = d.player1
        val card = d.putCardInHand(p, "Triarch Praetorian")
        val before = d.getHand(p).size
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertEquals(before - 1, d.getHand(p).size)
        assertEquals(20, d.getLifeTotal(p))
        assertTrue(d.projected.hasKeyword(card, Keyword.FLYING))
        assertEquals(2, d.projected.getPower(card))
    }

    @Test fun unearthTriggersTwoDrawsTwoLifeLossAndDelayedExile() {
        val d = customScenario(TriarchPraetorian)
        val p = d.player1
        val card = d.putCardInGraveyard(p, "Triarch Praetorian")
        val before = d.getHand(p).size
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.BLACK)
        assertNull(d.activate(p, card).error)
        d.resolveAll()
        assertTrue(card in d.getCreatures(p))
        assertEquals(before + 2, d.getHand(p).size)
        assertEquals(18, d.getLifeTotal(p))
        assertTrue(d.projected.hasKeyword(card, Keyword.HASTE))
        assertTrue(d.projected.hasKeyword(card, Keyword.FLYING))
        d.passPriorityUntil(Step.END)
        d.resolveAll()
        assertFalse(card in d.getPermanents(p))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Triarch Praetorian"))
    }
}
