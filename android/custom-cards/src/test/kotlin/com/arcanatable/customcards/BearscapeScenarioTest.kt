package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Bearscape
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class BearscapeScenarioTest {
    @Test
    fun exilesTwoGraveyardCardsToCreateABear() {
        val d = scenario()
        d.registerCard(Bearscape)
        val p = d.player1
        val card1 = d.putCardInGraveyard(p, "Plains")
        val card2 = d.putCardInGraveyard(p, "Forest")
        val enchantment = d.putPermanentOnBattlefield(p, "Bearscape")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        d.activate(p, enchantment)
        d.resolveAll()

        assertEquals(1, d.permanentsNamed(p, "Bear Token").size)
        assertTrue(d.zoneNames(p, Zone.EXILE).containsAll(listOf("Plains", "Forest")))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Plains"))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Forest"))
    }
}
