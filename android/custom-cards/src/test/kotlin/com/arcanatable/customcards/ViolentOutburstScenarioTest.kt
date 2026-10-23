package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ViolentOutburst
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class ViolentOutburstScenarioTest {
    @Test
    fun cascadesIntoCheaperSpellAndGivesYourCreaturesPlusOnePower() {
        val d = customScenario(ViolentOutburst, "Grizzly Bears", "Craw Wurm")
        val player = d.player1
        val ownCreature = d.putCreatureOnBattlefield(player, "Craw Wurm")
        val opponentCreature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putCardOnTopOfLibrary(player, "Grizzly Bears")
        val outburst = d.putCardInHand(player, "Violent Outburst")
        d.giveMana(player, Color.GREEN)
        d.giveMana(player, Color.RED)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpell(player, outburst).error)
        d.resolveAll()

        assertEquals(1, d.permanentsNamed(player, "Grizzly Bears").size)
        assertEquals(7, d.projected.getPower(ownCreature))
        assertEquals(2, d.projected.getPower(opponentCreature))
        assertEquals(listOf("Violent Outburst"), d.zoneNames(player, Zone.GRAVEYARD))
    }
}
