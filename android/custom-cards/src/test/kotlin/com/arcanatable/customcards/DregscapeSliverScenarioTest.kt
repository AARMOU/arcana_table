package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DregscapeSliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class DregscapeSliverScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(DregscapeSliver, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Dregscape Sliver")
        val sliverCard = d.putCardInGraveyard(p, "Metallic Sliver")
        d.putCreatureOnBattlefield(p, "Dregscape Sliver")
        d.giveColorlessMana(p, 2)
        assertTrue("Dregscape Sliver was registered in the real game", d.cardRegistry.getCard("Dregscape Sliver") != null)
        assertTrue(d.cardRegistry.getCard("Dregscape Sliver")!!.script.staticAbilities.isNotEmpty())
        assertEquals("Metallic Sliver", d.getCardName(sliverCard))
        assertTrue("Sliver creature cards in your graveyard gain unearth", d.abilityActions(p, sliverCard).isNotEmpty())
    }
}
