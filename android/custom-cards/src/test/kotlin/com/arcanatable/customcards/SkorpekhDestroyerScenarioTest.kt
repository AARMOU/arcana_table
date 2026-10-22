package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SkorpekhDestroyer
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class SkorpekhDestroyerScenarioTest {
    @Test
    fun artifactCreatureEnteringTriggersItsOwnFirstStrikeAndRetainsDeathtouch() {
        val d = customScenario(SkorpekhDestroyer)
        val p = d.player1
        val card = d.putCardInHand(p, "Skorpekh Destroyer")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.BLACK, 2)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        val creature = d.findPermanent(p, "Skorpekh Destroyer")!!
        assertTrue(d.projected.hasKeyword(creature, Keyword.DEATHTOUCH))
        assertTrue(d.projected.hasKeyword(creature, Keyword.FIRST_STRIKE))
        assertEquals(4, d.projected.getPower(creature))
        assertEquals(2, d.projected.getToughness(creature))
    }

    @Test
    fun otherFriendlyArtifactsRenewFirstStrikeButEnemyArtifactsDoNot() {
        val d = customScenario(SkorpekhDestroyer, "Ornithopter")
        val p = d.player1
        val creature = d.putCreatureOnBattlefield(p, "Skorpekh Destroyer")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertFalse(d.projected.hasKeyword(creature, Keyword.FIRST_STRIKE))
        assertTrue(d.projected.hasKeyword(creature, Keyword.DEATHTOUCH))
        d.putPermanentOnBattlefield(d.player2, "Ornithopter")
        d.resolveAll()
        assertFalse(d.projected.hasKeyword(creature, Keyword.FIRST_STRIKE))
        d.putPermanentOnBattlefield(p, "Ornithopter")
        d.resolveAll()
        assertTrue(d.projected.hasKeyword(creature, Keyword.FIRST_STRIKE))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        assertFalse(d.projected.hasKeyword(creature, Keyword.FIRST_STRIKE))
        assertTrue(d.projected.hasKeyword(creature, Keyword.DEATHTOUCH))
    }
}
