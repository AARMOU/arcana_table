package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GeierReachSanitarium
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class GeierReachSanitariumScenarioTest {
    @Test
    fun landProducesColorlessMana() {
        val d = customScenario(GeierReachSanitarium)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Geier Reach Sanitarium")
        assertEquals(2, d.cardRegistry.getCard("Geier Reach Sanitarium")!!.script.activatedAbilities.size)
        assertNull(d.activate(p, land, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun twoPlayersEachDrawThenDiscardOneCard() {
        val d = customScenario(GeierReachSanitarium)
        val p = d.player1
        val q = d.player2
        val land = d.putPermanentOnBattlefield(p, "Geier Reach Sanitarium")
        val handP = d.getHand(p).size
        val handQ = d.getHand(q).size
        val graveP = d.zoneNames(p, Zone.GRAVEYARD).size
        val graveQ = d.zoneNames(q, Zone.GRAVEYARD).size
        d.giveColorlessMana(p, 2)
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll(Choices(pick = listOf("Plains")))
        assertEquals(handP, d.getHand(p).size)
        assertEquals(handQ, d.getHand(q).size)
        assertEquals(graveP + 1, d.zoneNames(p, Zone.GRAVEYARD).size)
        assertEquals(graveQ + 1, d.zoneNames(q, Zone.GRAVEYARD).size)
        assertTrue(d.isTapped(land))
    }
}
