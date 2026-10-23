package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ShrineOfTheForsakenGods
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class ShrineOfTheForsakenGodsScenarioTest {
    @Test
    fun sevenLandsUnlockTheDoubleColorlessAbilityAndItCanPayForColorlessSpell() {
        val d = customScenario(ShrineOfTheForsakenGods, "Ulamog's Crusher")
        val p = d.player1
        val shrine = d.putPermanentOnBattlefield(p, "Shrine of the Forsaken Gods")
        repeat(6) { d.putPermanentOnBattlefield(p, "Plains") }
        assertNull(d.activate(p, shrine, index = 1).error)
        d.resolveAll()
        d.giveColorlessMana(p, 6)
        val card = d.putCardInHand(p, "Ulamog's Crusher")
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Ulamog's Crusher").size)
    }

    @Test
    fun fewerThanSevenLandsCannotActivateDoubleColorlessAbility() {
        val d = customScenario(ShrineOfTheForsakenGods)
        val p = d.player1
        val shrine = d.putPermanentOnBattlefield(p, "Shrine of the Forsaken Gods")
        repeat(5) { d.putPermanentOnBattlefield(p, "Plains") }
        assertNotNull(d.activate(p, shrine, index = 1).error)
        assertNull(d.activate(p, shrine, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
    }
}
