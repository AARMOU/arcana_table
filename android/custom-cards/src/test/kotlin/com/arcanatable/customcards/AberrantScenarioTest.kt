package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Aberrant
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class AberrantScenarioTest {
    @Test fun ravenousXFiveAddsFiveCountersAndDrawsCard() {
        val d = customScenario(Aberrant)
        val p = d.player1
        val card = d.putCardInHand(p, "Aberrant")
        val before = d.getHand(p).size
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 6)
        assertNull(d.castXSpell(p, card, 5).error)
        d.resolveAll()
        assertEquals(5, d.projected.getPower(card))
        assertEquals(5, d.projected.getToughness(card))
        assertTrue(d.projected.hasKeyword(card, Keyword.TRAMPLE))
        assertEquals(before, d.getHand(p).size)
    }

    @Test fun damageDestroysOnlyArtifactControlledByDamagedPlayer() {
        val d = customScenario(Aberrant, "Sol Ring")
        val p = d.player1
        val own = d.putPermanentOnBattlefield(p, "Sol Ring")
        val opponent = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val attacker = d.putCardInHand(p, "Aberrant")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 4)
        assertNull(d.castXSpell(p, attacker, 3).error)
        d.resolveAll()
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(attacker), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()
        assertEquals(17, d.getLifeTotal(d.player2))
        assertTrue(own in d.getPermanents(p))
        assertFalse(opponent in d.getPermanents(d.player2))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Sol Ring"))
    }
}
