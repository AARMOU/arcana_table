package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Scaretiller
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class ScaretillerScenarioTest {
    @Test
    fun tapTriggerCanPutLandFromGraveyardOntoBattlefieldTapped() {
        val d = customScenario(Scaretiller, "Forest", "Grizzly Bears")
        val player = d.player1
        val scaretiller = d.putCreatureOnBattlefield(player, "Scaretiller")
        d.removeSummoningSickness(scaretiller)
        d.putCardInGraveyard(player, "Forest")
        d.putCardInHand(player, "Grizzly Bears")
        val handSizeBeforeActivation = d.zoneNames(player, Zone.HAND).size
        d.giveColorlessMana(player, 2)

        assertNull(d.activate(player, scaretiller).error)
        d.resolveAll(Choices(pick = listOf("Forest")))

        val forest = d.permanentsNamed(player, "Forest").single()
        assertTrue("The triggered land enters tapped", d.isTapped(forest))
        assertFalse(d.zoneNames(player, Zone.GRAVEYARD).contains("Forest"))
        assertEquals("The activated ability draws one card, then discards one card",
            handSizeBeforeActivation, d.zoneNames(player, Zone.HAND).size)
    }

    @Test
    fun triggerCanTakeLandFromHandAndActivationRequiresItsManaCost() {
        val d = customScenario(Scaretiller, "Forest", "Grizzly Bears")
        val player = d.player1
        val scaretiller = d.putCreatureOnBattlefield(player, "Scaretiller")
        d.removeSummoningSickness(scaretiller)
        d.putCardInHand(player, "Forest")

        assertNotNull("The draw-discard ability costs {2}", d.activate(player, scaretiller).error)

        d.giveColorlessMana(player, 2)
        assertNull(d.activate(player, scaretiller).error)
        d.resolveAll(Choices(pick = listOf("Forest")))

        val land = d.permanentsNamed(player, "Forest").single()
        assertTrue(d.isTapped(land))
        assertTrue(d.zoneNames(player, Zone.HAND).none { it == "Forest" })
    }

    @Test
    fun tapTriggerMayBeDeclinedEvenWhenLandsAreAvailable() {
        val d = customScenario(Scaretiller, "Grizzly Bears")
        val player = d.player1
        val scaretiller = d.putCreatureOnBattlefield(player, "Scaretiller")
        d.removeSummoningSickness(scaretiller)
        val landsBefore = d.getLands(player).size
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Plains"))
        d.giveColorlessMana(player, 2)

        assertNull(d.activate(player, scaretiller).error)
        d.resolveAll()

        assertEquals("Declining the optional trigger puts no land onto the battlefield",
            landsBefore, d.getLands(player).size)
    }
}
