package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BelakorTheDarkMaster
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BelakorTheDarkMasterScenarioTest {
    @Test
    fun entryCountsBelakorAndOtherDemonsThenDrawsAndLosesThatMuchLife() {
        val d = customScenario(BelakorTheDarkMaster)
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Great Unclean One")
        val belakor = d.putCardInHand(p, "Be'lakor, the Dark Master")
        val handBeforeCast = d.getHandSize(p)
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, belakor).error)
        d.resolveAll()

        assertEquals(18, d.getLifeTotal(p))
        assertEquals(handBeforeCast + 1, d.getHandSize(p))
        assertEquals(20, d.getLifeTotal(d.player2))
    }

    @Test
    fun anotherDemonDealsItsPowerToAnyTargetButOtherCreaturesDoNotTrigger() {
        val d = customScenario(BelakorTheDarkMaster, "Grizzly Bears")
        val p = d.player1
        val belakor = d.putCardInHand(p, "Be'lakor, the Dark Master")
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, belakor).error)
        d.resolveAll()
        assertEquals(19, d.getLifeTotal(p))

        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.resolveAll()
        assertEquals(20, d.getLifeTotal(d.player2))

        val demon = d.putCardInHand(p, "Great Unclean One")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, demon).error)
        d.resolveAll(Choices(targets = listOf(d.player2)))
        assertEquals(16, d.getLifeTotal(d.player2))
    }
}
