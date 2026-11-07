package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Tervigon
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TervigonScenarioTest {
    @Test
    fun ravenousXFiveDrawsAndCombatDamageCreatesThatManyTyranids() {
        val d = customScenario(Tervigon)
        val p = d.player1
        val initialHandSize = d.zoneNames(p, Zone.HAND).size
        val tervigon = d.putCardInHand(p, "Tervigon")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 6)

        assertNull(d.castXSpell(p, tervigon, 5).error)
        d.resolveAll()

        assertEquals(5, d.projected.getPower(tervigon))
        assertEquals(5, d.projected.getToughness(tervigon))
        assertEquals(initialHandSize + 1, d.zoneNames(p, Zone.HAND).size)
        d.removeSummoningSickness(tervigon)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(tervigon), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(15, d.getLifeTotal(d.player2))
        assertEquals(5, d.permanentsNamed(p, "Tyranid").size)
    }

    @Test
    fun combatDamageToACreatureDoesNotCreateDamageScaledTokens() {
        val d = customScenario(Tervigon, "Grizzly Bears")
        val p = d.player1
        val tervigon = d.putCardInHand(p, "Tervigon")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 3)
        assertNull(d.castXSpell(p, tervigon, 2).error)
        d.resolveAll()
        d.removeSummoningSickness(tervigon)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(tervigon), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(tervigon))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(d.player2))
        assertEquals(0, d.permanentsNamed(p, "Tyranid").size)
    }
}
