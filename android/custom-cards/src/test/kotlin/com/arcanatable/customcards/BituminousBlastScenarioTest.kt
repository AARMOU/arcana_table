package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BituminousBlast
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class BituminousBlastScenarioTest {
    @Test
    fun cascadesAndDealsFourDamageToTargetCreature() {
        val d = customScenario(BituminousBlast, "Craw Wurm", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(opponent, "Craw Wurm")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Bituminous Blast")
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, spell, listOf(target)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(opponent, "Craw Wurm").isEmpty())
        assertEquals(1, d.permanentsNamed(p, "Grizzly Bears").size)
    }
}
