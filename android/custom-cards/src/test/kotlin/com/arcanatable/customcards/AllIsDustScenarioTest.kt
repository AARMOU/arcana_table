package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AllIsDust
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class AllIsDustScenarioTest {
    @Test
    fun bothPlayersSacrificeColoredPermanentsButKeepColorlessArtifactsAndLands() {
        val d = customScenario(AllIsDust, "Grizzly Bears", "Craw Wurm", "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(opponent, "Craw Wurm")
        d.putPermanentOnBattlefield(p, "Sol Ring")
        d.putPermanentOnBattlefield(opponent, "Sol Ring")
        val spell = d.putCardInHand(p, "All Is Dust")
        d.giveColorlessMana(p, 7)

        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.permanentsNamed(opponent, "Craw Wurm").isEmpty())
        assertEquals(1, d.permanentsNamed(p, "Sol Ring").size)
        assertEquals(1, d.permanentsNamed(opponent, "Sol Ring").size)
    }
}
