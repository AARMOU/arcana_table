package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ChandrasIgnition
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class ChandrasIgnitionScenarioTest {
    @Test
    fun dealsTargetPowerToOtherCreaturesAndEachOpponentWithTargetAsSource() {
        val d = customScenario(ChandrasIgnition, "Craw Wurm", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val wurm = d.putCreatureOnBattlefield(p, "Craw Wurm")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Chandra's Ignition")
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, spell, listOf(wurm)).error)
        d.resolveStack()

        assertEquals(14, d.getLifeTotal(opponent))
        assertEquals(1, d.permanentsNamed(p, "Craw Wurm").size)
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.permanentsNamed(opponent, "Grizzly Bears").isEmpty())
    }
}
