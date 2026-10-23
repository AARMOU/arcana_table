package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ScreamerKiller
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class ScreamerKillerScenarioTest {
    @Test fun castingCostlyCreatureDealsExactlyFiveToChosenOpponent() {
        val d = customScenario(ScreamerKiller, "Craw Wurm")
        val p = d.player1
        val killer = d.putCreatureOnBattlefield(p, "Screamer-Killer")
        assertTrue(d.projected.hasKeyword(killer, Keyword.TRAMPLE))
        val wurm = d.putCardInHand(p, "Craw Wurm")
        d.giveMana(p, Color.GREEN)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, wurm).error)
        d.resolveAll(Choices(targets = listOf(d.player2)))
        assertEquals(15, d.getLifeTotal(d.player2))
        assertEquals(20, d.getLifeTotal(p))
    }

    @Test fun lowManaValueCreatureDoesNotTrigger() {
        val d = customScenario(ScreamerKiller, "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Screamer-Killer")
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()
        assertEquals(20, d.getLifeTotal(d.player2))
        assertEquals(0, d.stackSize)
    }
}
