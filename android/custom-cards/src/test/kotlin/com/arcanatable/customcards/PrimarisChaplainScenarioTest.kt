package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PrimarisChaplain
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class PrimarisChaplainScenarioTest {
    @Test
    fun attackPumpsOnlyOtherAttackersAndProtectsOnlyChaplainUntilEndOfTurn() {
        val d = customScenario(PrimarisChaplain, "Grizzly Bears")
        val p = d.player1
        val chaplain = d.putCreatureOnBattlefield(p, "Primaris Chaplain")
        val attacker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val reserve = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.removeSummoningSickness(chaplain)
        d.removeSummoningSickness(attacker)
        assertFalse(d.projected.hasKeyword(chaplain, Keyword.INDESTRUCTIBLE))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(chaplain, attacker), d.player2).error)
        d.resolveAll()

        assertEquals(3, d.projected.getPower(chaplain))
        assertEquals(3, d.projected.getPower(attacker))
        assertEquals(2, d.projected.getToughness(attacker))
        assertEquals(2, d.projected.getPower(reserve))
        assertTrue(d.projected.hasKeyword(chaplain, Keyword.INDESTRUCTIBLE))
        assertFalse(d.projected.hasKeyword(attacker, Keyword.INDESTRUCTIBLE))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertEquals(2, d.projected.getPower(attacker))
        assertFalse(d.projected.hasKeyword(chaplain, Keyword.INDESTRUCTIBLE))
    }

    @Test
    fun attackingAloneStillGainsIndestructibleWithoutPumpingItself() {
        val d = customScenario(PrimarisChaplain)
        val chaplain = d.putCreatureOnBattlefield(d.player1, "Primaris Chaplain")
        d.removeSummoningSickness(chaplain)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(chaplain), d.player2).error)
        d.resolveAll()
        assertEquals(3, d.projected.getPower(chaplain))
        assertTrue(d.projected.hasKeyword(chaplain, Keyword.INDESTRUCTIBLE))
    }
}
