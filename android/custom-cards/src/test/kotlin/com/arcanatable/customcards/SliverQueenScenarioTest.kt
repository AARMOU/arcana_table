package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SliverQueen
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.activate
import com.arcanatable.customcards.support.permanentsNamed
import com.arcanatable.customcards.support.resolveAll
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SliverQueenScenarioTest {
    @Test
    fun spendsTwoGenericManaToCreateColorlessSliverToken() {
        val d = CardPool.driver("Grizzly Bears")
        d.registerCard(SliverQueen)
        d.initMirrorMatch(Deck.of("Grizzly Bears" to 40))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val queen = d.putCreatureOnBattlefield(d.player1, "Sliver Queen")
        d.giveColorlessMana(d.player1, 2)

        assertNull(d.activate(d.player1, queen).error)
        d.resolveAll()

        assertEquals(1, d.permanentsNamed(d.player1, "Sliver Token").size)
    }
}
