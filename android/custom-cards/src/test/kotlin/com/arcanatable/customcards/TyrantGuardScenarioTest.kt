package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TyrantGuard
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class TyrantGuardScenarioTest {
    @Test
    fun ravenousEntersWithPaidXCountersAndDrawsOnlyForXAtLeastFive() {
        val d = customScenario(TyrantGuard)
        val p = d.player1
        val initialHand = d.zoneNames(p, Zone.HAND).size
        val guard = d.putCardInHand(p, "Tyrant Guard")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 7)
        assertNull(d.castXSpell(p, guard, 5).error)
        d.resolveAll()
        assertEquals(8, d.projected.getPower(guard))
        assertEquals(8, d.projected.getToughness(guard))
        // A successful ravenous X=5 entry replaces the cast card by drawing one.
        assertEquals(initialHand + 1, d.zoneNames(p, Zone.HAND).size)

        val other = d.putCardInHand(p, "Tyrant Guard")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 4)
        assertNull(d.castXSpell(p, other, 2).error)
        d.resolveAll()
        assertEquals(5, d.projected.getPower(other))
        assertEquals(5, d.projected.getToughness(other))
        assertEquals(initialHand + 1, d.zoneNames(p, Zone.HAND).size)
    }

    @Test
    fun sacrificingGuardProtectsOnlyFriendlyCounterBearingCreaturesForThisTurn() {
        val d = customScenario(TyrantGuard, "Stonecoil Serpent", "Grizzly Bears")
        val p = d.player1
        val guard = d.putCreatureOnBattlefield(p, "Tyrant Guard")
        val serpent = d.putCardInHand(p, "Stonecoil Serpent")
        d.giveColorlessMana(p, 3)
        assertNull(d.castXSpell(p, serpent, 3).error)
        d.resolveAll()
        val ownBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemyBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        assertFalse(d.projected.hasKeyword(serpent, Keyword.HEXPROOF))
        assertNull(d.activate(p, guard, index = 0).error)
        d.resolveAll()
        assertFalse(guard in d.getCreatures(p))
        assertTrue(d.projected.hasKeyword(serpent, Keyword.HEXPROOF))
        assertTrue(d.projected.hasKeyword(serpent, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(ownBear, Keyword.HEXPROOF))
        assertFalse(d.projected.hasKeyword(ownBear, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(enemyBear, Keyword.INDESTRUCTIBLE))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertFalse(d.projected.hasKeyword(serpent, Keyword.HEXPROOF))
        assertFalse(d.projected.hasKeyword(serpent, Keyword.INDESTRUCTIBLE))
    }
}
