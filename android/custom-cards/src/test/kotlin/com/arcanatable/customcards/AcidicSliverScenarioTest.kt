package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AcidicSliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class AcidicSliverScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(AcidicSliver, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Acidic Sliver")
        val sliver = d.putCreatureOnBattlefield(p, "Metallic Sliver")
        d.giveColorlessMana(p, 2)
        assertTrue("Acidic Sliver was registered in the real game", d.cardRegistry.getCard("Acidic Sliver") != null)
        assertTrue("Acidic Sliver grants its sacrifice-for-damage ability", d.abilityActions(p, sliver).isNotEmpty())
    }
}
