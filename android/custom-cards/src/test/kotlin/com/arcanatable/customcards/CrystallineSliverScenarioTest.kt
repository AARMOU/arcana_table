package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CrystallineSliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class CrystallineSliverScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(CrystallineSliver, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Crystalline Sliver")
        val sliver = d.putCreatureOnBattlefield(p, "Metallic Sliver")
        assertTrue("Crystalline Sliver was registered in the real game", d.cardRegistry.getCard("Crystalline Sliver") != null)
        assertTrue(d.projected.hasKeyword(sliver, Keyword.SHROUD))
    }
}
