package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ThunderwolfCavalry
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class ThunderwolfCavalryScenarioTest {
    @Test
    fun firstStrikeDamageAddsOneCounterToEachOtherFriendlyCreatureOnly() {
        val d = customScenario(ThunderwolfCavalry, "Grizzly Bears")
        val p = d.player1
        val cavalry = d.putCreatureOnBattlefield(p, "Thunderwolf Cavalry")
        val firstAlly = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val secondAlly = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(cavalry)

        assertTrue(d.projected.hasKeyword(cavalry, Keyword.FIRST_STRIKE))
        assertEquals(2, d.projected.getPower(firstAlly))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(cavalry), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()

        assertEquals(16, d.getLifeTotal(d.player2))
        assertEquals(4, d.projected.getPower(cavalry))
        assertEquals(4, d.projected.getToughness(cavalry))
        for (ally in listOf(firstAlly, secondAlly)) {
            assertEquals(3, d.projected.getPower(ally))
            assertEquals(3, d.projected.getToughness(ally))
        }
        assertEquals(2, d.projected.getPower(enemy))
    }

    @Test
    fun doesNotDistributeCountersWithoutCombatDamage() {
        val d = customScenario(ThunderwolfCavalry, "Grizzly Bears")
        val ally = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player1, "Thunderwolf Cavalry")
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertEquals(2, d.projected.getPower(ally))
        assertEquals(2, d.projected.getToughness(ally))
    }
}
