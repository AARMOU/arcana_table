package com.arcanatable.customcards

import com.arcanatable.customcards.cards.WhispererOfTheWilds
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class WhispererOfTheWildsScenarioTest {
    @Test
    fun tapsForOneGreenWithoutFerociousAndCannotActivateAgainWhileTapped() {
        val d = customScenario(WhispererOfTheWilds)
        val p = d.player1
        val whisperer = d.putPermanentOnBattlefield(p, "Whisperer of the Wilds")

        assertNull(d.activate(p, whisperer, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).green)
        assertTrue(d.isTapped(whisperer))
        assertNotNull(d.activate(p, whisperer, index = 0).error)
        assertEquals(1, d.manaPool(p).green)
    }

    @Test
    fun ferociousAbilityNeedsAControlledCreatureWithPowerAtLeastFour() {
        val d = customScenario(WhispererOfTheWilds, "Centaur Courser", "Craw Wurm")
        val p = d.player1
        val whisperer = d.putPermanentOnBattlefield(p, "Whisperer of the Wilds")
        d.putCreatureOnBattlefield(p, "Centaur Courser")
        d.putCreatureOnBattlefield(d.player2, "Craw Wurm")

        assertNotNull(d.activate(p, whisperer, index = 1).error)
        assertFalse(d.isTapped(whisperer))
        assertEquals(0, d.manaPool(p).green)
    }

    @Test
    fun ferociousAbilityTapsForTwoGreenWhenYouControlACreatureWithPowerAtLeastFour() {
        val d = customScenario(WhispererOfTheWilds, "Craw Wurm")
        val p = d.player1
        val whisperer = d.putPermanentOnBattlefield(p, "Whisperer of the Wilds")
        d.putCreatureOnBattlefield(p, "Craw Wurm")

        assertNull(d.activate(p, whisperer, index = 1).error)
        d.resolveAll()
        assertEquals(2, d.manaPool(p).green)
        assertTrue(d.isTapped(whisperer))
    }
}
