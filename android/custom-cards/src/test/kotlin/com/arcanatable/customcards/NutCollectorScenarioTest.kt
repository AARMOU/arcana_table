package com.arcanatable.customcards

import com.arcanatable.customcards.cards.NutCollector
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class NutCollectorScenarioTest {
    @Test
    fun upkeepMakesSquirrelAndThresholdPumpsSquirrels() {
        val d = CardPool.driver().apply {
            registerCard(NutCollector)
            initMirrorMatch(Deck.of("Forest" to 40))
        }
        val p = d.player1
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        d.putCreatureOnBattlefield(p, "Nut Collector")
        val squirrel = d.putCreatureOnBattlefield(p, "Squirrel")
        repeat(7) { d.putCardInGraveyard(p, "Forest") }
        assertEquals(3, d.projected.getPower(squirrel))
        d.passPriorityUntil(Step.END, activePlayer = p)
        d.passPriorityUntil(Step.UPKEEP, activePlayer = p)
        d.resolveAll()
        assertEquals(2, d.getCreatures(p).count { d.getCardName(it) in setOf("Squirrel", "Squirrel Token") })
    }
}
