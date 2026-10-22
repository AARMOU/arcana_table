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
        // Direct fixture placement does not emit an enters event. Cast each artifact
        // through the rules engine so the trigger has a real zone-change event.
        val enemyArtifact = d.putCardInHand(d.player2, "Ornithopter")
        assertNull(d.castSpell(d.player2, enemyArtifact).error)
        d.resolveAll()
        assertFalse(d.projected.hasKeyword(creature, Keyword.FIRST_STRIKE))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        val friendlyArtifact = d.putCardInHand(p, "Ornithopter")
        assertNull(d.castSpell(p, friendlyArtifact).error)
        d.resolveAll()
        assertTrue(d.projected.hasKeyword(creature, Keyword.FIRST_STRIKE))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertFalse(d.projected.hasKeyword(creature, Keyword.FIRST_STRIKE))
        assertTrue(d.projected.hasKeyword(creature, Keyword.DEATHTOUCH))
    }
}
