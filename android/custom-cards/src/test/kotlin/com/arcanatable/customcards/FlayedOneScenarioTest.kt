package com.arcanatable.customcards

import com.arcanatable.customcards.cards.FlayedOne
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class FlayedOneScenarioTest {
    @Test fun castMillsExactlyThreeFromControllersLibrary() {
        val d = customScenario(FlayedOne)
        val p = d.player1
        val before = d.zoneNames(p, Zone.GRAVEYARD).size
        val opponentBefore = d.zoneNames(d.player2, Zone.GRAVEYARD).size
        val card = d.putCardInHand(p, "Flayed One")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertEquals(before + 3, d.zoneNames(p, Zone.GRAVEYARD).size)
        assertEquals(opponentBefore, d.zoneNames(d.player2, Zone.GRAVEYARD).size)
        assertEquals(4, d.projected.getPower(card))
        assertTrue(d.projected.hasKeyword(card, Keyword.LIFELINK))
    }

    @Test fun combatLifelinkGainsFourLifeForController() {
        val d = customScenario(FlayedOne)
        val p = d.player1
        val creature = d.putCreatureOnBattlefield(p, "Flayed One")
        d.removeSummoningSickness(creature)
        val before = d.getLifeTotal(p)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(creature), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()
        assertEquals(before + 4, d.getLifeTotal(p))
        assertEquals(16, d.getLifeTotal(d.player2))
    }
}
