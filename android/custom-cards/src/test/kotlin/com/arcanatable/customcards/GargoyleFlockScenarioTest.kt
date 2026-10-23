package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GargoyleFlock
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class GargoyleFlockScenarioTest {
    @Test
    fun castingTheFlockItselfQualifiesForSkyswarmAtEndStep() {
        val d = customScenario(GargoyleFlock)
        val p = d.player1
        val flock = d.putCardInHand(p, "Gargoyle Flock")
        d.giveColorlessMana(p, 2)
        d.giveMana(p, Color.GREEN)
        d.giveMana(p, Color.BLUE)
        assertNull(d.castSpell(p, flock).error)
        d.resolveAll()
        val parent = d.findPermanent(p, "Gargoyle Flock")!!
        assertTrue(d.projected.hasKeyword(parent, Keyword.FLYING))

        d.passPriorityUntil(Step.END, activePlayer = p)
        d.resolveAll()
        val gargoyles = d.getCreatures(p).filter {
            it != parent && d.getCardName(it)?.contains("Tyranid Gargoyle") == true
        }
        assertEquals(1, gargoyles.size)
        assertEquals(1, d.projected.getPower(gargoyles.single()))
        assertEquals(1, d.projected.getToughness(gargoyles.single()))
        assertTrue(d.projected.hasKeyword(gargoyles.single(), Keyword.FLYING))
    }
}
