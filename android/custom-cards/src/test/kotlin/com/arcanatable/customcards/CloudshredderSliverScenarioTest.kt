package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CloudshredderSliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class CloudshredderSliverScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(CloudshredderSliver, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Cloudshredder Sliver")
        val sliver = d.putCreatureOnBattlefield(p, "Metallic Sliver")
        assertTrue("Cloudshredder Sliver was registered in the real game", d.cardRegistry.getCard("Cloudshredder Sliver") != null)
        assertTrue(d.projected.hasKeyword(sliver, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(sliver, Keyword.HASTE))
    }
}
