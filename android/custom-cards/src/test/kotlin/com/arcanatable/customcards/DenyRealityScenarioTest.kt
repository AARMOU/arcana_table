package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DenyReality
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class DenyRealityScenarioTest {
    @Test
    fun cascadesAndReturnsTargetPermanentToOwnersHand() {
        val d = customScenario(DenyReality, "Craw Wurm", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(opponent, "Craw Wurm")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Deny Reality")
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, spell, listOf(target)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(opponent, "Craw Wurm").isEmpty())
        assertTrue(d.zoneNames(opponent, Zone.HAND).contains("Craw Wurm"))
        assertEquals(1, d.permanentsNamed(p, "Grizzly Bears").size)
    }
}
