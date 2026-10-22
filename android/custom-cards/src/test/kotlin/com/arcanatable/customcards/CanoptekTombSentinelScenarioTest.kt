package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CanoptekTombSentinel
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class CanoptekTombSentinelScenarioTest {
    @Test fun castingFromHandDoesNotExileOpponentsNonlandPermanent() {
        val d = customScenario(CanoptekTombSentinel, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val sentinel = d.putCardInHand(p, "Canoptek Tomb Sentinel")
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, sentinel).error)
        d.resolveAll()
        assertTrue(sentinel in d.getCreatures(p))
        assertTrue(artifact in d.getPermanents(d.player2))
        assertTrue(d.projected.hasKeyword(sentinel, Keyword.VIGILANCE))
    }

    @Test fun unearthFromGraveyardExilesChosenNonlandThenSentinelAtEndStep() {
        val d = customScenario(CanoptekTombSentinel, "Sol Ring")
        val p = d.player1
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val sentinel = d.putCardInGraveyard(p, "Canoptek Tomb Sentinel")
        d.giveColorlessMana(p, 7)
        assertNull(d.activate(p, sentinel).error)
        d.resolveAll(Choices(targets = listOf(artifact)))
        assertTrue(sentinel in d.getCreatures(p))
        assertTrue(d.projected.hasKeyword(sentinel, Keyword.HASTE))
        assertFalse(artifact in d.getPermanents(d.player2))
        assertTrue(d.zoneNames(d.player2, Zone.EXILE).contains("Sol Ring"))
        d.passPriorityUntil(Step.END)
        d.resolveAll()
        assertFalse(sentinel in d.getPermanents(p))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Canoptek Tomb Sentinel"))
    }
}
