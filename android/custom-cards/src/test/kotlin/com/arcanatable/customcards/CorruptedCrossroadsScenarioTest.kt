package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CorruptedCrossroads
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class CorruptedCrossroadsScenarioTest {
    @Test
    fun coloredManaCostsLifeAndCannotCastColoredSpells() {
        val d = customScenario(CorruptedCrossroads, "Grizzly Bears")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Corrupted Crossroads")

        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))

        assertEquals(39, d.getLifeTotal(p))
        assertEquals(1, d.manaPool(p).green)
        val bears = d.putCardInHand(p, "Grizzly Bears")
        assertNotNull(d.castSpell(p, bears).error)
    }

    @Test
    fun coloredManaMayPayGenericCostOfColorlessSpell() {
        val d = customScenario(CorruptedCrossroads, "Bane of Bala Ged")
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Corrupted Crossroads")
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))

        d.giveColorlessMana(p, 6)
        val bane = d.putCardInHand(p, "Bane of Bala Ged")
        assertNull(d.castSpell(p, bane).error)
        d.resolveAll()

        assertEquals(1, d.permanentsNamed(p, "Bane of Bala Ged").size)
    }
}
