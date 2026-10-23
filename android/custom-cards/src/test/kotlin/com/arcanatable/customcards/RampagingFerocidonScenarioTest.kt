package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RampagingFerocidon
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.resolveAll
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RampagingFerocidonScenarioTest {
    @Test
    fun creatureEntriesDamageTheirControllerAndLifeGainIsPrevented() {
        val d = CardPool.driver("Essence Warden", "Goblin Piker").apply {
            registerCard(RampagingFerocidon)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val ferocidon = d.putCardInHand(p, "Rampaging Ferocidon")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, ferocidon).error)
        d.resolveAll()

        val warden = d.putCardInHand(p, "Essence Warden")
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, warden).error)
        d.resolveAll()
        assertEquals(19, d.getLifeTotal(p)) // Ferocidon damages the Warden's controller.

        val piker = d.putCardInHand(p, "Goblin Piker")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, piker).error)
        d.resolveAll()

        // Ferocidon deals another damage, while Essence Warden's life gain is prevented.
        assertEquals(18, d.getLifeTotal(p))
    }
}
