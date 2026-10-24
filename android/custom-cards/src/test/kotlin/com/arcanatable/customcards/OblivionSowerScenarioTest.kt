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
    @Test
    fun mayChooseZeroExiledLandsWithoutReversingTheExile() {
        val d = customScenario(OblivionSower, "Forest", "Mountain", "Sol Ring", "Grizzly Bears")
        val caster = d.player1
        val opponent = d.player2
        d.putCardInExile(opponent, "Forest")
        listOf("Forest", "Mountain", "Sol Ring", "Grizzly Bears").forEach {
            d.putCardOnTopOfLibrary(opponent, it)
        }
        val sower = d.putCardInHand(caster, "Oblivion Sower")
        d.giveColorlessMana(caster, 6)

        assertNull(d.castSpell(caster, sower).error)
        // "May put any number" includes zero. An unmatched choice declines all optional lands.
        d.resolveAll(Choices(targets = listOf(opponent), pick = listOf("Decline all lands")))

        assertEquals(0, d.permanentsNamed(caster, "Forest").size)
        assertEquals(0, d.permanentsNamed(caster, "Mountain").size)
        assertEquals(2, d.zoneNames(opponent, Zone.EXILE).count { it == "Forest" })
        assertTrue(d.zoneNames(opponent, Zone.EXILE).containsAll(
            listOf("Mountain", "Sol Ring", "Grizzly Bears")
        ))
        assertNotNull(d.findPermanent(caster, "Oblivion Sower"))
    }

    @Test
    fun preexistingExiledLandsMayBeChosenEvenWhenTopFourAreNonlands() {
        val d = customScenario(OblivionSower, "Forest", "Grizzly Bears")
        val caster = d.player1
        val opponent = d.player2
        d.putCardInExile(opponent, "Forest")
        repeat(4) { d.putCardOnTopOfLibrary(opponent, "Grizzly Bears") }
        val sower = d.putCardInHand(caster, "Oblivion Sower")
        d.giveColorlessMana(caster, 6)

        assertNull(d.castSpell(caster, sower).error)
        d.resolveAll(Choices(targets = listOf(opponent), pick = listOf("Forest")))

        assertEquals(1, d.permanentsNamed(caster, "Forest").size)
        assertEquals(4, d.zoneNames(opponent, Zone.EXILE).count { it == "Grizzly Bears" })
        assertFalse(d.zoneNames(opponent, Zone.EXILE).contains("Forest"))
        assertNotNull(d.findPermanent(caster, "Oblivion Sower"))
    }

}
