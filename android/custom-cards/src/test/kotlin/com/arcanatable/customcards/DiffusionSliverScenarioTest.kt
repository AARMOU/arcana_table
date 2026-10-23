package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DiffusionSliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class DiffusionSliverScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(DiffusionSliver, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Diffusion Sliver")
        val sliver = d.putCreatureOnBattlefield(p, "Metallic Sliver")
        assertTrue("Diffusion Sliver was registered in the real game", d.cardRegistry.getCard("Diffusion Sliver") != null)
        assertTrue("grants ward", d.projected.hasKeyword(sliver, Keyword.WARD))
    }
}
