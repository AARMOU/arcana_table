package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Cryptothrall
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class CryptothrallIndependentCertificationTest {
    @Test
    fun castingRequiresFourGenericAndEntersAsAThreeThree() {
        val d = customScenario(Cryptothrall)
        val player = d.player1
        val card = d.putCardInHand(player, "Cryptothrall")
        d.giveColorlessMana(player, 3)
        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Cryptothrall"))

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()
        val cryptothrall = d.permanentsNamed(player, "Cryptothrall").single()
        assertEquals(3, d.projected.getPower(cryptothrall))
        assertEquals(3, d.projected.getToughness(cryptothrall))
    }

    @Test
    fun opponentsCannotTargetAnotherArtifactCreatureYouControl() {
        val d = customScenario(Cryptothrall, "Murder", "Ornithopter")
        val protectedArtifact = d.putCreatureOnBattlefield(d.player1, "Ornithopter")
        d.putCreatureOnBattlefield(d.player1, "Cryptothrall")
        val murder = d.putCardInHand(d.player2, "Murder")
        d.giveMana(d.player2, Color.BLACK, 2)
        d.giveColorlessMana(d.player2, 1)

        assertNotNull(d.castSpell(d.player2, murder, listOf(protectedArtifact)).error)
        assertTrue(d.permanentsNamed(d.player1, "Ornithopter").contains(protectedArtifact))
        assertTrue(d.zoneNames(d.player2, Zone.HAND).contains("Murder"))
    }

    @Test
    fun youCanTargetAnArtifactCreatureThatHasHexproofFromCryptothrall() {
        val d = customScenario(Cryptothrall, "Murder", "Ornithopter")
        val player = d.player1
        d.putCreatureOnBattlefield(player, "Cryptothrall")
        val artifact = d.putCreatureOnBattlefield(player, "Ornithopter")
        val murder = d.putCardInHand(player, "Murder")
        d.giveMana(player, Color.BLACK, 2)
        d.giveColorlessMana(player, 1)

        assertNull(d.castSpell(player, murder, listOf(artifact)).error)
        d.resolveAll()

        assertFalse(d.permanentsNamed(player, "Ornithopter").contains(artifact))
        assertTrue(d.zoneNames(player, Zone.GRAVEYARD).contains("Ornithopter"))
    }

    @Test
    fun cryptothrallDoesNotGrantHexproofToItself() {
        val d = customScenario(Cryptothrall, "Murder")
        val cryptothrall = d.putCreatureOnBattlefield(d.player1, "Cryptothrall")
        val murder = d.putCardInHand(d.player2, "Murder")
        d.giveMana(d.player2, Color.BLACK, 2)
        d.giveColorlessMana(d.player2, 1)

        assertNull(d.castSpell(d.player2, murder, listOf(cryptothrall)).error)
        d.resolveAll()

        assertFalse(d.permanentsNamed(d.player1, "Cryptothrall").contains(cryptothrall))
        assertTrue(d.zoneNames(d.player1, Zone.GRAVEYARD).contains("Cryptothrall"))
    }

    @Test
    fun cryptothrallDoesNotProtectANonartifactCreatureYouControl() {
        val d = customScenario(Cryptothrall, "Murder", "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player1, "Cryptothrall")
        val murder = d.putCardInHand(d.player2, "Murder")
        d.giveMana(d.player2, Color.BLACK, 2)
        d.giveColorlessMana(d.player2, 1)

        assertNull(d.castSpell(d.player2, murder, listOf(bear)).error)
        d.resolveAll()

        assertFalse(d.permanentsNamed(d.player1, "Grizzly Bears").contains(bear))
    }

    @Test
    fun cryptothrallDoesNotProtectAnOpponentsArtifactCreature() {
        val d = customScenario(Cryptothrall, "Murder", "Ornithopter")
        d.putCreatureOnBattlefield(d.player1, "Cryptothrall")
        val opposingArtifact = d.putCreatureOnBattlefield(d.player2, "Ornithopter")
        val murder = d.putCardInHand(d.player1, "Murder")
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 1)

        assertNull(d.castSpell(d.player1, murder, listOf(opposingArtifact)).error)
        d.resolveAll()

        assertFalse(d.permanentsNamed(d.player2, "Ornithopter").contains(opposingArtifact))
    }
}
