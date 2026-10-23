package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AcornHarvest
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class AcornHarvestScenarioTest {
    @Test
    fun resolvesIntoTwoSquirrels() {
        val d = CardPool.driver().apply {
            registerCard(AcornHarvest)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val harvest = d.putCardInHand(p, "Acorn Harvest")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, harvest).error)
        d.resolveAll()
        assertEquals(2, d.getCreatures(p).count { d.getCardName(it) == "Squirrel Token" })
    }
}
