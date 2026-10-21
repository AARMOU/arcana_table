package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ForTheEmperor
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class ForTheEmperorScenarioTest {
    @Test
    fun improvesOnlyYourCreaturesAndGrantsBothKeywords() {
        val d = customScenario(ForTheEmperor, "Grizzly Bears", "Craw Wurm")
        val p = d.player1
        val ownBear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val otherWurm = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        val spell = d.putCardInHand(p, "For the Emperor!")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()

        assertEquals(4, d.projected.getPower(ownBear))
        assertEquals(4, d.projected.getToughness(ownBear))
        assertTrue(d.projected.hasKeyword(ownBear, Keyword.VIGILANCE))
        assertTrue(d.projected.hasKeyword(ownBear, Keyword.LIFELINK))
        assertEquals(6, d.projected.getPower(otherWurm))
        assertFalse(d.projected.hasKeyword(otherWurm, Keyword.LIFELINK))
    }
}
