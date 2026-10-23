package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HibernationSliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class HibernationSliverScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(HibernationSliver, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Hibernation Sliver")
        val sliver = d.putCreatureOnBattlefield(p, "Metallic Sliver")
        assertTrue("Hibernation Sliver was registered in the real game", d.cardRegistry.getCard("Hibernation Sliver") != null)
        assertTrue("grants a life-payment bounce ability", d.abilityActions(p, sliver).isNotEmpty())
    }
}
