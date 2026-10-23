package com.arcanatable.customcards

import com.arcanatable.customcards.cards.FreneticSliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class FreneticSliverScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(FreneticSliver, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Frenetic Sliver")
        val sliver = d.putCreatureOnBattlefield(p, "Metallic Sliver")
        assertTrue("Frenetic Sliver was registered in the real game", d.cardRegistry.getCard("Frenetic Sliver") != null)
        assertTrue("grants a zero-cost activated ability", d.abilityActions(p, sliver).isNotEmpty())
    }
}
