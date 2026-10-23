package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PlagueDrone
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class PlagueDroneScenarioTest {
    @Test
    fun opposingLifeGainBecomesLossWhileControllersLifeGainIsUnchanged() {
        val d = customScenario(PlagueDrone, "Essence Warden", "Grizzly Bears")
        val p = d.player1
        val drone = d.putCreatureOnBattlefield(p, "Plague Drone")
        d.putCreatureOnBattlefield(p, "Essence Warden")
        d.putCreatureOnBattlefield(d.player2, "Essence Warden")
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()

        assertTrue(d.projected.hasKeyword(drone, Keyword.FLYING))
        assertEquals(21, d.getLifeTotal(p))
        assertEquals(19, d.getLifeTotal(d.player2))
    }

    @Test
    fun twoDronesReplaceOneLifeGainOnlyOnce() {
        val d = customScenario(PlagueDrone, "Essence Warden", "Grizzly Bears")
        val p = d.player1
        repeat(2) { d.putCreatureOnBattlefield(p, "Plague Drone") }
        d.putCreatureOnBattlefield(d.player2, "Essence Warden")
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(p))
        assertEquals(19, d.getLifeTotal(d.player2))
    }

    @Test
    fun replacementEndsWhenDroneLeavesBattlefield() {
        val d = customScenario(PlagueDrone, "Essence Warden", "Murder", "Grizzly Bears")
        val p = d.player1
        val drone = d.putCreatureOnBattlefield(p, "Plague Drone")
        d.putCreatureOnBattlefield(d.player2, "Essence Warden")
        val murder = d.putCardInHand(p, "Murder")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, murder, listOf(drone)).error)
        d.resolveAll()
        assertNull(d.findPermanent(p, "Plague Drone"))

        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()
        assertEquals(21, d.getLifeTotal(d.player2))
    }
}
