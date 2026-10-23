package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TraverseTheOutlands
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class TraverseTheOutlandsScenarioTest {
    @Test
    fun findsUpToTheGreatestCreaturePowerInTappedBasics() {
        val d = scenario("Grizzly Bears")
        d.registerCard(TraverseTheOutlands)
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Grizzly Bears")
        repeat(2) { d.putCardOnTopOfLibrary(p, "Forest") }
        val spell = d.putCardInHand(p, "Traverse the Outlands")
        d.giveMana(p, Color.GREEN, 5)

        d.castSpell(p, spell)
        d.resolveAll(Choices(pick = listOf("Forest")))

        assertEquals(2, d.permanentsNamed(p, "Forest").size)
        assertTrue(d.getLands(p).all { d.isTapped(it) })
    }
}
