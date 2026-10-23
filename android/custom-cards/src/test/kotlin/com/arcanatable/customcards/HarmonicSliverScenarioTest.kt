package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HarmonicSliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class HarmonicSliverScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(HarmonicSliver, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Harmonic Sliver")
        val sliver = d.putCreatureOnBattlefield(p, "Metallic Sliver")
        assertTrue("Harmonic Sliver was registered in the real game", d.cardRegistry.getCard("Harmonic Sliver") != null)
        assertNotNull(sliver)
        assertTrue(d.cardRegistry.getCard("Harmonic Sliver")!!.script.staticAbilities.isNotEmpty())
    }
}
