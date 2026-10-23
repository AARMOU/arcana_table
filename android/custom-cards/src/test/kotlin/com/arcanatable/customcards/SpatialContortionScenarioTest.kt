package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SpatialContortion
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class SpatialContortionScenarioTest {
    @Test
    fun pumpAndShrinkApplySimultaneouslyUntilEndOfTurn() {
        val d = customScenario(SpatialContortion, "Craw Wurm")
        val p = d.player1
        val wurm = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        val spell = d.putCardInHand(p, "Spatial Contortion")
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, spell, listOf(wurm)).error)
        d.resolveStack()

        assertEquals(9, d.projected.getPower(wurm))
        assertEquals(1, d.projected.getToughness(wurm))
    }

    @Test
    fun shrinkingToughnessBelowOneKillsCreature() {
        val d = customScenario(SpatialContortion, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Spatial Contortion")
        d.giveColorlessMana(p, 2)

        assertNull(d.castSpell(p, spell, listOf(bear)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
    }
}
