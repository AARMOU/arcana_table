package com.arcanatable.customcards
import com.arcanatable.customcards.cards.Mutilate
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test
class MutilateScenarioTest {
    @Test fun swampCountShrinksBothPlayersCreaturesAtResolution() {
        val d = customScenario(Mutilate, "Grizzly Bears", "Craw Wurm")
        val p = d.player1
        repeat(2) { d.putPermanentOnBattlefield(p, "Swamp") }
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val wurm = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        val spell = d.putCardInHand(p, "Mutilate")
        repeat(2) { d.giveMana(p, Color.BLACK) }
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertEquals(4, d.projected.getPower(wurm))
        assertEquals(2, d.projected.getToughness(wurm))
    }
}
