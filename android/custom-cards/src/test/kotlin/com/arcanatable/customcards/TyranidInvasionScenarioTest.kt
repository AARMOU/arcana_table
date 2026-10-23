package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TyranidInvasion
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class TyranidInvasionScenarioTest {
    @Test
    fun twoPlayerGameMakesOneThreeThreeTyranidWarriorWithTrample() {
        val d = customScenario(TyranidInvasion)
        val p = d.player1
        val spell = d.putCardInHand(p, "Tyranid Invasion")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()

        val warrior = d.getCreatures(p).filter { d.getCardName(it)?.contains("Tyranid") == true }
        assertEquals(1, warrior.size)
        assertEquals(3, d.projected.getPower(warrior.single()))
        assertEquals(3, d.projected.getToughness(warrior.single()))
        assertTrue(d.projected.hasKeyword(warrior.single(), Keyword.TRAMPLE))
    }
}
