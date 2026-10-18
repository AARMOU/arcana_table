package com.arcanatable.customcards

import com.arcanatable.customcards.cards.QuicksilverAmulet
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class QuicksilverAmuletScenarioTest {
    @Test
    fun activatedAbilityPutsACreatureFromHandOntoTheBattlefield() {
        val d = customScenario(QuicksilverAmulet, "Grizzly Bears")
        val player = d.player1
        val amulet = d.putPermanentOnBattlefield(player, "Quicksilver Amulet")
        d.putCardInHand(player, "Grizzly Bears")
        d.giveColorlessMana(player, 4)

        assertNull(d.activate(player, amulet).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))

        assertEquals(1, d.permanentsNamed(player, "Grizzly Bears").size)
    }
}
