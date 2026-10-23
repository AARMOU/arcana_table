package com.arcanatable.customcards

import com.arcanatable.customcards.cards.LeechingSliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class LeechingSliverScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(LeechingSliver, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Leeching Sliver")
        val sliver = d.putCreatureOnBattlefield(p, "Metallic Sliver")
        assertTrue("Leeching Sliver was registered in the real game", d.cardRegistry.getCard("Leeching Sliver") != null)
        assertNotNull(sliver)
        assertTrue(d.cardRegistry.getCard("Leeching Sliver")!!.script.staticAbilities.isNotEmpty())
    }
}
