package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HomingSliver
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class HomingSliverScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(HomingSliver, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Homing Sliver")
        val sliverCard = d.putCardInHand(p, "Metallic Sliver")
        d.giveColorlessMana(p, 3)
        assertTrue("Homing Sliver was registered in the real game", d.cardRegistry.getCard("Homing Sliver") != null)
        assertTrue("Sliver cards in hand gain slivercycling", d.abilityActions(p, sliverCard).isNotEmpty())
    }
}
