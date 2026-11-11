package com.arcanatable.customcards

import com.arcanatable.customcards.cards.NurglesRot
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class NurglesRotIndependentCertificationTest {
    @Test
    fun oneBlackManaCastsNurglesRotOnAnOpponentsCreature() {
        val d = customScenario(NurglesRot, "Grizzly Bears")
        val player = d.player1
        val creature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val aura = d.putCardInHand(player, "Nurgle's Rot")
        d.giveMana(player, Color.BLACK)

        assertNull(d.castSpell(player, aura, listOf(creature)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Nurgle's Rot").contains(aura))
        assertFalse(d.permanentsNamed(d.player2, "Nurgle's Rot").contains(aura))
    }

    @Test
    fun cannotEnchantACreatureYouControl() {
        val d = customScenario(NurglesRot, "Grizzly Bears")
        val player = d.player1
        val creature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val aura = d.putCardInHand(player, "Nurgle's Rot")
        d.giveMana(player, Color.BLACK)

        assertNotNull(d.castSpell(player, aura, listOf(creature)).error)

        assertTrue(d.zoneNames(player, Zone.HAND).contains("Nurgle's Rot"))
        assertTrue(d.permanentsNamed(player, "Nurgle's Rot").isEmpty())
        assertTrue(d.permanentsNamed(player, "Grizzly Bears").contains(creature))
    }

    @Test
    fun cannotEnchantAnOpponentsNoncreaturePermanent() {
        val d = customScenario(NurglesRot, "Plains")
        val player = d.player1
        val land = d.putPermanentOnBattlefield(d.player2, "Plains")
        val aura = d.putCardInHand(player, "Nurgle's Rot")
        d.giveMana(player, Color.BLACK)

        assertNotNull(d.castSpell(player, aura, listOf(land)).error)

        assertTrue(d.zoneNames(player, Zone.HAND).contains("Nurgle's Rot"))
        assertTrue(d.permanentsNamed(player, "Nurgle's Rot").isEmpty())
        assertTrue(d.permanentsNamed(d.player2, "Plains").contains(land))
    }

    @Test
    fun colorlessManaCannotPayTheBlackManaCost() {
        val d = customScenario(NurglesRot, "Grizzly Bears")
        val player = d.player1
        val creature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val aura = d.putCardInHand(player, "Nurgle's Rot")
        d.giveColorlessMana(player, 1)

        assertNotNull(d.castSpell(player, aura, listOf(creature)).error)

        assertTrue(d.zoneNames(player, Zone.HAND).contains("Nurgle's Rot"))
        assertTrue(d.permanentsNamed(player, "Nurgle's Rot").isEmpty())
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").contains(creature))
    }

    @Test
    fun enchantedCreatureDyingReturnsTheAuraAndCreatesTheSpecifiedDemon() {
        val d = customScenario(NurglesRot, "Grizzly Bears", "Murder")
        val player = d.player1
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val aura = d.putCardInHand(player, "Nurgle's Rot")
        d.giveMana(player, Color.BLACK)
        assertNull(d.castSpell(player, aura, listOf(victim)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Nurgle's Rot").contains(aura))
        assertTrue(d.permanentsNamed(player, "Plaguebearer of Nurgle").isEmpty())

        val murder = d.putCardInHand(player, "Murder")
        d.giveMana(player, Color.BLACK, 2)
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, murder, listOf(victim)).error)
        d.resolveAll()

        assertTrue(d.getGraveyard(d.player2).contains(victim))
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Nurgle's Rot"))
        assertTrue(d.permanentsNamed(player, "Nurgle's Rot").isEmpty())
        val tokens = d.permanentsNamed(player, "Plaguebearer of Nurgle")
        assertEquals(1, tokens.size)
        assertTrue(d.permanentsNamed(d.player2, "Plaguebearer of Nurgle").isEmpty())
        val token = tokens.single()
        assertTrue(d.projected.hasType(token, "CREATURE"))
        assertTrue(d.projected.hasSubtype(token, "DEMON"))
        assertTrue(d.projected.hasColor(token, Color.BLACK))
        assertEquals(1, d.projected.getBasePower(token))
        assertEquals(3, d.projected.getBaseToughness(token))
    }

    @Test
    fun deathOfAnUnenchantedCreatureDoesNotTriggerNurglesRot() {
        val d = customScenario(NurglesRot, "Grizzly Bears", "Murder")
        val player = d.player1
        val enchanted = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val unenchanted = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val aura = d.putCardInHand(player, "Nurgle's Rot")
        d.giveMana(player, Color.BLACK)
        assertNull(d.castSpell(player, aura, listOf(enchanted)).error)
        d.resolveAll()

        val murder = d.putCardInHand(player, "Murder")
        d.giveMana(player, Color.BLACK, 2)
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, murder, listOf(unenchanted)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").contains(enchanted))
        assertTrue(d.getGraveyard(d.player2).contains(unenchanted))
        assertTrue(d.permanentsNamed(player, "Nurgle's Rot").contains(aura))
        assertFalse(d.zoneNames(player, Zone.HAND).contains("Nurgle's Rot"))
        assertTrue(d.permanentsNamed(player, "Plaguebearer of Nurgle").isEmpty())
    }

    @Test
    fun exilingTheEnchantedCreatureDoesNotTriggerTheDiesAbility() {
        val d = customScenario(NurglesRot, "Grizzly Bears", "Path to Exile")
        val player = d.player1
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val aura = d.putCardInHand(player, "Nurgle's Rot")
        d.giveMana(player, Color.BLACK)
        assertNull(d.castSpell(player, aura, listOf(victim)).error)
        d.resolveAll()

        val path = d.putCardInHand(player, "Path to Exile")
        d.giveMana(player, Color.WHITE)
        assertNull(d.castSpell(player, path, listOf(victim)).error)
        d.resolveAll()

        assertTrue(d.getExile(d.player2).contains(victim))
        assertTrue(d.getGraveyardCardNames(player).contains("Nurgle's Rot"))
        assertFalse(d.zoneNames(player, Zone.HAND).contains("Nurgle's Rot"))
        assertTrue(d.permanentsNamed(player, "Plaguebearer of Nurgle").isEmpty())
    }
}
