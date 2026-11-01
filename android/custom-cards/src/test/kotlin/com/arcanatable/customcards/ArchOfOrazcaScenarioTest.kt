package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ArchOfOrazca
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.player.PlayerCitysBlessingComponent
import org.junit.Assert.*
import org.junit.Test

class ArchOfOrazcaScenarioTest {
    @Test
    fun colorlessManaAbilityAddsOneAndTapsTheLand() {
        val d = customScenario(ArchOfOrazca)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Arch of Orazca")

        assertNull(d.activate(p, land, index = 0).error)
        assertTrue(d.isTapped(land))
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun ascendRequiresTenPermanentsYouControlAndBlessingPersists() {
        val d = customScenario(ArchOfOrazca, "Forest")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Arch of Orazca")
        val forests = (1..8).map { d.putPermanentOnBattlefield(p, "Forest") }
        repeat(10) { d.putPermanentOnBattlefield(d.player2, "Forest") }
        d.giveColorlessMana(p, 5)

        assertFalse(d.state.getEntity(p)!!.has<PlayerCitysBlessingComponent>())
        assertNotNull("The opponent's permanents do not count toward ascend",
            d.activate(p, land, index = 1).error)
        assertFalse(d.isTapped(land))
        assertEquals(5, d.manaPool(p).colorless)

        d.putPermanentOnBattlefield(p, "Forest")
        assertNull(d.passPriority(p).error)
        assertNull(d.passPriority(d.player2).error)
        assertTrue(d.state.getEntity(p)!!.has<PlayerCitysBlessingComponent>())
        val handSize = d.getHand(p).size
        assertNull(d.activate(p, land, index = 1).error)
        assertTrue(d.isTapped(land))
        d.resolveAll()
        assertEquals(handSize + 1, d.getHand(p).size)
        assertEquals(0, d.manaPool(p).colorless)

        d.moveToGraveyard(forests[0])
        d.moveToGraveyard(forests[1])
        assertTrue(d.state.getEntity(p)!!.has<PlayerCitysBlessingComponent>())
    }
}
