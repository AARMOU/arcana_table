package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HexmarkDestroyer
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class HexmarkDestroyerScenarioTest {
    @Test fun ordinaryCastIsSixSixAndSixBlockersAreRequired() {
        val d = customScenario(HexmarkDestroyer, "Grizzly Bears")
        val p = d.player1
        val card = d.putCardInHand(p, "Hexmark Destroyer")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.BLACK, 2)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        assertEquals(6, d.projected.getPower(card))
        d.removeSummoningSickness(card)
        val blockers = (1..6).map { d.putCreatureOnBattlefield(d.player2, "Grizzly Bears") }
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(card), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(d.player2, mapOf(blockers.first() to listOf(card))).error)
        assertNull(d.declareBlockers(d.player2, blockers.associateWith { listOf(card) }).error)
    }

    @Test fun unearthFromGraveyardGrantsHasteAndExilesAtNextEndStep() {
        val d = customScenario(HexmarkDestroyer)
        val p = d.player1
        val card = d.putCardInGraveyard(p, "Hexmark Destroyer")
        d.giveColorlessMana(p, 4)
        d.giveMana(p, Color.BLACK, 2)
        assertNull(d.activate(p, card).error)
        d.resolveAll()
        assertTrue(card in d.getPermanents(p))
        assertTrue(d.projected.hasKeyword(card, Keyword.HASTE))
        d.passPriorityUntil(Step.END)
        d.resolveAll()
        assertFalse(card in d.getPermanents(p))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Hexmark Destroyer"))
    }
}
