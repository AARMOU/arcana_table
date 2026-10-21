package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SanguinaryPriest
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class SanguinaryPriestScenarioTest {
    @Test
    fun alliedCreatureDeathTriggersOneDamageToChosenOpponent() {
        val d = customScenario(SanguinaryPriest, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val priest = d.putCreatureOnBattlefield(p, "Sanguinary Priest")
        val dyingBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val removal = d.putCardInHand(p, "Doom Blade")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 1)

        assertTrue(d.projected.hasKeyword(priest, Keyword.LIFELINK))
        assertNull(d.castSpell(p, removal, listOf(dyingBear)).error)
        d.resolveAll(Choices(targets = listOf(d.player2)))

        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertEquals(19, d.getLifeTotal(d.player2))
    }
}
