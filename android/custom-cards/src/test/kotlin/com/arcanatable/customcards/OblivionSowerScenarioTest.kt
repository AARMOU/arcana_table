package com.arcanatable.customcards

import com.arcanatable.customcards.cards.OblivionSower
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class OblivionSowerScenarioTest {
    @Test
    fun castTriggerExilesFourAndLetsYouTakeAnyOwnedLandsFromExile() {
        val d = customScenario(OblivionSower, "Forest", "Mountain", "Sol Ring", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        d.putCardInExile(opponent, "Forest")
        listOf("Forest", "Mountain", "Sol Ring", "Grizzly Bears").forEach {
            d.putCardOnTopOfLibrary(opponent, it)
        }
        val sower = d.putCardInHand(p, "Oblivion Sower")
        d.giveColorlessMana(p, 6)

        assertNull(d.castSpell(p, sower).error)
        d.resolveAll(Choices(targets = listOf(opponent), pick = listOf("Forest", "Mountain")))

        assertEquals(2, d.permanentsNamed(p, "Forest").size)
        assertEquals(1, d.permanentsNamed(p, "Mountain").size)
        assertNotNull(d.findPermanent(p, "Oblivion Sower"))
        assertTrue(d.zoneNames(opponent, Zone.EXILE).containsAll(listOf("Sol Ring", "Grizzly Bears")))
    }
}
