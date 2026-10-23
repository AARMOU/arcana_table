package com.arcanatable.customcards

import com.arcanatable.customcards.cards.EndlessAtlas
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class EndlessAtlasScenarioTest {
    @Test
    fun threeSameNamedLandsEnableDrawAndTapIsPaid() {
        val d = customScenario(EndlessAtlas)
        val p = d.player1
        val atlas = d.putPermanentOnBattlefield(p, "Endless Atlas")
        repeat(3) { d.putPermanentOnBattlefield(p, "Plains") }
        val before = d.getHand(p).size
        d.giveColorlessMana(p, 2)
        assertNull(d.activate(p, atlas).error)
        assertTrue(d.isTapped(atlas))
        d.resolveAll()
        assertEquals(before + 1, d.getHand(p).size)
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun differentNamesAndOpponentsLandsDoNotSatisfyRestriction() {
        val d = customScenario(EndlessAtlas, "Island")
        val p = d.player1
        val atlas = d.putPermanentOnBattlefield(p, "Endless Atlas")
        repeat(2) { d.putPermanentOnBattlefield(p, "Plains") }
        d.putPermanentOnBattlefield(p, "Island")
        repeat(3) { d.putPermanentOnBattlefield(d.player2, "Plains") }
        d.giveColorlessMana(p, 2)
        assertNotNull(d.activate(p, atlas).error)
        assertFalse(d.isTapped(atlas))
        assertEquals(2, d.manaPool(p).colorless)
    }
}
