package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Venomthrope
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class VenomthropeScenarioTest {
    @Test
    fun hasAllThreePrintedKeywordsAndExactStats() {
        val d = customScenario(Venomthrope)
        val creature = d.putCreatureOnBattlefield(d.player1, "Venomthrope")

        assertEquals(2, d.projected.getPower(creature))
        assertEquals(2, d.projected.getToughness(creature))
        assertTrue(d.projected.hasKeyword(creature, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(creature, Keyword.DEATHTOUCH))
        assertTrue(d.projected.hasKeyword(creature, Keyword.HEXPROOF))
    }
}
