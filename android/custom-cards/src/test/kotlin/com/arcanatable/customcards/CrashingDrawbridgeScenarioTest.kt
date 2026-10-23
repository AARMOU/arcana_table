package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CrashingDrawbridge
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class CrashingDrawbridgeScenarioTest {
    @Test
    fun tappingTheDrawbridgeGrantsHasteOnlyToYourCreatures() {
        val d = customScenario(CrashingDrawbridge, "Grizzly Bears", "Craw Wurm")
        val p = d.player1
        val drawbridge = d.putCreatureOnBattlefield(p, "Crashing Drawbridge")
        d.removeSummoningSickness(drawbridge)
        val yourBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opponentsWurm = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        assertFalse(d.projected.hasKeyword(yourBear, Keyword.HASTE))

        assertNull(d.activate(p, drawbridge).error)
        d.resolveAll()

        assertTrue(d.projected.hasKeyword(yourBear, Keyword.HASTE))
        assertTrue(d.projected.hasKeyword(drawbridge, Keyword.DEFENDER))
        assertFalse(d.projected.hasKeyword(opponentsWurm, Keyword.HASTE))
    }
}
