package com.arcanatable.customcards

import com.arcanatable.customcards.cards.FungusSliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class FungusSliverScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(FungusSliver, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Fungus Sliver")
        val sliver = d.putCreatureOnBattlefield(p, "Metallic Sliver")
        assertTrue("Fungus Sliver was registered in the real game", d.cardRegistry.getCard("Fungus Sliver") != null)
        assertNotNull(sliver)
        assertTrue(d.cardRegistry.getCard("Fungus Sliver")!!.script.staticAbilities.isNotEmpty())
    }
}
