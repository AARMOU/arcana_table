package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BaneOfBalaGed
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class BaneOfBalaGedScenarioTest {
    @Test fun defendingPlayerChoosesTwoPermanentsToExileRatherThanSacrifice() {
        val d = customScenario(BaneOfBalaGed, "Grizzly Bears", "Sol Ring")
        val p = d.player1
        val bane = d.putCreatureOnBattlefield(p, "Bane of Bala Ged")
        d.removeSummoningSickness(bane)
        d.putPermanentOnBattlefield(p, "Plains")
        d.putPermanentOnBattlefield(d.player2, "Plains")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(bane), d.player2).error)
        d.bothPass()
        val decision = d.state.pendingDecision as? SelectCardsDecision
        assertNotNull("The defending player chooses which permanents leave", decision)
        assertEquals(d.player2, decision!!.playerId)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears", "Sol Ring")))
        assertEquals(setOf("Grizzly Bears", "Sol Ring"), d.zoneNames(d.player2, Zone.EXILE).toSet())
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).isEmpty())
        assertNotNull(d.findPermanent(p, "Plains"))
        assertNotNull(d.findPermanent(d.player2, "Plains"))
    }

    @Test fun defenderWithOnlyOnePermanentExilesThatPermanent() {
        val d = customScenario(BaneOfBalaGed)
        val p = d.player1
        val bane = d.putCreatureOnBattlefield(p, "Bane of Bala Ged")
        d.removeSummoningSickness(bane)
        d.putPermanentOnBattlefield(d.player2, "Plains")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(bane), d.player2).error)
        d.resolveAll()
        assertTrue(d.getPermanents(d.player2).isEmpty())
        assertEquals(listOf("Plains"), d.zoneNames(d.player2, Zone.EXILE))
    }
}
