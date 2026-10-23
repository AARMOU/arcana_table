package com.arcanatable.customcards

import com.arcanatable.customcards.cards.FrogSquirrels
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.GameTestDriver
import com.arcanatable.customcards.support.projected
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrogSquirrelsScenarioTest {
    @Test
    fun isAReachCreatureWithPrintedStats() {
        val d = CardPool.driver().apply {
            registerCard(FrogSquirrels)
            initMirrorMatch(Deck.of("Forest" to 40))
        }
        val squirrel = d.putCreatureOnBattlefield(d.player1, "Frog-Squirrels")
        assertEquals(2, d.projected.getPower(squirrel))
        assertEquals(2, d.projected.getToughness(squirrel))
        assertTrue(d.projected.hasKeyword(squirrel, com.wingedsheep.sdk.core.Keyword.REACH))
    }
}
