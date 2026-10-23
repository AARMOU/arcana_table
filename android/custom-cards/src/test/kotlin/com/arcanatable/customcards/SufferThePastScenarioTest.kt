package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SufferThePast
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SufferThePastScenarioTest {
    @Test
    fun xTargetsAreExiledAndLifeChangesByTheNumberActuallyExiled() {
        val d = customScenario(SufferThePast, "Grizzly Bears", "Forest")
        val p = d.player1
        val opponent = d.player2
        val bear = d.putCardInGraveyard(opponent, "Grizzly Bears")
        val forest = d.putCardInGraveyard(opponent, "Forest")
        d.putCardInGraveyard(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Suffer the Past")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)

        assertNotNull("X cards must be selected as targets", d.castXSpell(p, spell, 2, listOf(opponent, bear)).error)
        assertNull(d.castXSpell(p, spell, 2, listOf(opponent, bear, forest)).error)
        d.resolveAll()

        assertEquals(18, d.getLifeTotal(opponent))
        assertEquals(22, d.getLifeTotal(p))
        assertTrue(d.zoneNames(opponent, Zone.EXILE).containsAll(listOf("Grizzly Bears", "Forest")))
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).isEmpty())
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).containsAll(listOf("Grizzly Bears", "Suffer the Past")))
    }
}
