package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CopperGnomes
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class CopperGnomesScenarioTest {
    @Test
    fun activatedAbilityPutsAnArtifactFromHandOntoTheBattlefield() {
        val d = customScenario(CopperGnomes, "Sol Ring")
        val player = d.player1
        val gnomes = d.putCreatureOnBattlefield(player, "Copper Gnomes")
        d.putCardInHand(player, "Sol Ring")
        d.giveColorlessMana(player, 4)

        assertNull(d.activate(player, gnomes).error)
        assertNull(d.findPermanent(player, "Copper Gnomes"))
        d.resolveAll(Choices(yes = true, pick = listOf("Sol Ring")))

        assertEquals(1, d.permanentsNamed(player, "Sol Ring").size)
        assertFalse("Sol Ring remains in hand", "Sol Ring" in d.zoneNames(player, Zone.HAND))
    }

    @Test
    fun decliningTheMayStillPaysTheActivationCost() {
        val d = customScenario(CopperGnomes, "Sol Ring")
        val player = d.player1
        val gnomes = d.putCreatureOnBattlefield(player, "Copper Gnomes")
        d.putCardInHand(player, "Sol Ring")
        d.giveColorlessMana(player, 4)

        assertNull(d.activate(player, gnomes).error)
        d.resolveAll(Choices(yes = false))

        assertNull(d.findPermanent(player, "Copper Gnomes"))
        assertTrue("Sol Ring remains in hand", "Sol Ring" in d.zoneNames(player, Zone.HAND))
        assertTrue(d.permanentsNamed(player, "Sol Ring").isEmpty())
    }

    @Test
    fun cannotPutANonartifactCardFromHandOntoTheBattlefield() {
        val d = customScenario(CopperGnomes, "Grizzly Bears")
        val player = d.player1
        val gnomes = d.putCreatureOnBattlefield(player, "Copper Gnomes")
        d.putCardInHand(player, "Grizzly Bears")
        d.giveColorlessMana(player, 4)

        assertNull(d.activate(player, gnomes).error)
        d.resolveAll()

        assertNull(d.findPermanent(player, "Copper Gnomes"))
        assertTrue("Grizzly Bears remains in hand", "Grizzly Bears" in d.zoneNames(player, Zone.HAND))
        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isEmpty())
    }

    @Test
    fun activationRequiresFourManaAndTheController() {
        val d = customScenario(CopperGnomes, "Sol Ring")
        val player = d.player1
        val gnomes = d.putCreatureOnBattlefield(player, "Copper Gnomes")
        d.putCardInHand(player, "Sol Ring")
        d.giveColorlessMana(player, 3)

        assertNotNull(d.activate(d.player2, gnomes).error)
        assertNotNull(d.activate(player, gnomes).error)

        assertNotNull(d.findPermanent(player, "Copper Gnomes"))
        assertTrue("Sol Ring remains in hand", "Sol Ring" in d.zoneNames(player, Zone.HAND))
        assertEquals(3, d.manaPool(player).colorless)
    }
}
