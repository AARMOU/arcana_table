package com.arcanatable.customcards

import com.arcanatable.customcards.cards.LavabellySliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class LavabellySliverScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(LavabellySliver, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Lavabelly Sliver")
        val sliver = d.putCreatureOnBattlefield(p, "Metallic Sliver")
        assertTrue("Lavabelly Sliver was registered in the real game", d.cardRegistry.getCard("Lavabelly Sliver") != null)
        assertNotNull(sliver)
        assertTrue(d.cardRegistry.getCard("Lavabelly Sliver")!!.script.staticAbilities.isNotEmpty())
    }
}
