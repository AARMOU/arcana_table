package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BoscoJustABear
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class BoscoJustABearScenarioTest {
    @Test
    fun createsFoodForEachLegendaryCreatureIncludingBosco() {
        val d = scenario("Toski, Bearer of Secrets")
        d.registerCard(BoscoJustABear)
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Toski, Bearer of Secrets")
        d.giveMana(p, Color.GREEN, 5)
        val bosco = d.putCardInHand(p, "Bosco, Just a Bear")

        d.castSpell(p, bosco)
        d.resolveAll()

        assertEquals(2, d.permanentsNamed(p, "Food").size)
    }
}
