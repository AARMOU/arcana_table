package com.arcanatable.customcards

import com.arcanatable.customcards.cards.FirstSliversChosen
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class FirstSliversChosenScenarioTest {
    @Test
    fun customDefinitionRunsInArgentumScenario() {
        val d = customScenario(FirstSliversChosen, "Metallic Sliver")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "First Sliver's Chosen")
        val sliver = d.putCreatureOnBattlefield(p, "Metallic Sliver")
        assertTrue("First Sliver's Chosen was registered in the real game", d.cardRegistry.getCard("First Sliver's Chosen") != null)
        assertTrue(d.projected.hasKeyword(sliver, Keyword.EXALTED))
    }
}
