package com.arcanatable.customcards

import com.arcanatable.customcards.cards.WilsonRefinedGrizzly
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.scripting.KeywordAbility
import org.junit.Assert.*
import org.junit.Test

class WilsonRefinedGrizzlyScenarioTest {
    @Test
    fun entersAsUncounterableWardTwoVigilantReachTrampler() {
        val d = scenario()
        d.registerCard(WilsonRefinedGrizzly)
        val wilson = d.putPermanentOnBattlefield(d.player1, "Wilson, Refined Grizzly")

        assertEquals(2, d.projected.getPower(wilson))
        assertTrue(d.projected.hasKeyword(wilson, Keyword.VIGILANCE))
        assertTrue(d.projected.hasKeyword(wilson, Keyword.REACH))
        assertTrue(d.projected.hasKeyword(wilson, Keyword.TRAMPLE))
        assertTrue(WilsonRefinedGrizzly.script.cantBeCountered)
        assertTrue(WilsonRefinedGrizzly.keywordAbilities.any { it is KeywordAbility.Ward })
    }
}
