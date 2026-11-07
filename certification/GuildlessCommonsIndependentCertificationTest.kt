package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GuildlessCommons
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class GuildlessCommonsIndependentCertificationTest {
    @Test
    fun entersTappedAndReturnsALandYouControlWhenItsEntryTriggerResolves() {
        val d = customScenario(GuildlessCommons)
        val player = d.player1
        val forest = d.putLandOnBattlefield(player, "Forest")
        val commons = d.putCardInHand(player, "Guildless Commons")

        assertNull(d.playLand(player, commons).error)
        assertTrue(d.isTapped(commons))
        assertTrue(d.permanentsNamed(player, "Forest").contains(forest))

        d.resolveAll(Choices(pick = listOf("Forest")))

        assertTrue(d.state.getZone(player, Zone.HAND).contains(forest))
        assertFalse(d.permanentsNamed(player, "Forest").contains(forest))
        assertTrue(d.permanentsNamed(player, "Guildless Commons").contains(commons))
        assertTrue(d.isTapped(commons))
    }

    @Test
    fun tappingGuildlessCommonsAddsTwoColorlessManaAndTapsIt() {
        val d = customScenario(GuildlessCommons)
        val player = d.player1
        val commons = d.putLandOnBattlefield(player, "Guildless Commons")
        d.untapPermanent(commons)

        assertEquals(0, d.manaPool(player).colorless)
        assertNull(d.activate(player, commons).error)
        d.resolveAll()

        assertEquals(2, d.manaPool(player).colorless)
        assertEquals(0, d.manaPool(player).white)
        assertEquals(0, d.manaPool(player).blue)
        assertEquals(0, d.manaPool(player).black)
        assertEquals(0, d.manaPool(player).red)
        assertEquals(0, d.manaPool(player).green)
        assertTrue(d.isTapped(commons))
        assertNotNull(d.activate(player, commons).error)
        assertEquals(2, d.manaPool(player).colorless)
    }

    @Test
    fun entryTriggerCannotReturnAnOpponentsLand() {
        val d = customScenario(GuildlessCommons)
        val player = d.player1
        val opponent = d.player2
        val plains = d.putLandOnBattlefield(opponent, "Plains")
        val commons = d.putCardInHand(player, "Guildless Commons")

        assertNull(d.playLand(player, commons).error)
        d.resolveAll(Choices(pick = listOf("Plains")))

        assertTrue(d.permanentsNamed(opponent, "Plains").contains(plains))
        assertFalse(d.state.getZone(player, Zone.HAND).contains(plains))
        assertTrue(d.state.getZone(player, Zone.HAND).contains(commons))
        assertFalse(d.permanentsNamed(player, "Guildless Commons").contains(commons))
    }

    @Test
    fun entryTriggerCanReturnGuildlessCommonsItselfWhenItIsYourOnlyLand() {
        val d = customScenario(GuildlessCommons)
        val player = d.player1
        val commons = d.putCardInHand(player, "Guildless Commons")

        assertNull(d.playLand(player, commons).error)
        assertTrue(d.isTapped(commons))
        d.resolveAll(Choices(pick = listOf("Guildless Commons")))

        assertTrue(d.state.getZone(player, Zone.HAND).contains(commons))
        assertTrue(d.permanentsNamed(player, "Guildless Commons").isEmpty())
    }
}
