package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SeaGateWreckage
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SeaGateWreckageScenarioTest {
    @Test
    fun colorlessManaAbilityProducesOneColorless() {
        val d = customScenario(SeaGateWreckage)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Sea Gate Wreckage")
        assertEquals(2, d.cardRegistry.getCard("Sea Gate Wreckage")!!.script.activatedAbilities.size)
        assertNull(d.activate(p, land, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun drawAbilityRequiresEmptyHandAtActivation() {
        val d = customScenario(SeaGateWreckage)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Sea Gate Wreckage")
        assertTrue(d.getHand(p).isNotEmpty())
        d.giveColorlessMana(p, 3)
        assertNotNull("The draw ability is forbidden while cards remain in hand",
            d.activate(p, land, index = 1).error)
        assertFalse(d.isTapped(land))

        d.getHand(p).toList().forEach { d.moveToGraveyard(it) }
        assertTrue(d.getHand(p).isEmpty())
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll()
        assertEquals(1, d.getHand(p).size)
        assertEquals(1, d.zoneNames(p, Zone.HAND).size)
        assertTrue(d.isTapped(land))
    }
}
