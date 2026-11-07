package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HeraldsOfTzeentch
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class HeraldsOfTzeentchIndependentCertificationTest {
    private val name = "Heralds of Tzeentch"

    @Test
    fun castingRequiresFourGenericAndOneBlueMana() {
        val short = customScenario(HeraldsOfTzeentch)
        val shortCard = short.putCardInHand(short.player1, name)
        short.giveColorlessMana(short.player1, 3)
        short.giveMana(short.player1, Color.BLUE)

        assertNotNull(short.castSpell(short.player1, shortCard).error)
        assertTrue(short.getHand(short.player1).contains(shortCard))
        assertEquals(0, short.stackSize)
        assertEquals(3, short.manaPool(short.player1).colorless)
        assertEquals(1, short.manaPool(short.player1).blue)

        val missingBlue = customScenario(HeraldsOfTzeentch)
        val missingBlueCard = missingBlue.putCardInHand(missingBlue.player1, name)
        missingBlue.giveColorlessMana(missingBlue.player1, 4)

        assertNotNull(missingBlue.castSpell(missingBlue.player1, missingBlueCard).error)
        assertTrue(missingBlue.getHand(missingBlue.player1).contains(missingBlueCard))
        assertEquals(0, missingBlue.stackSize)

        val paid = customScenario(HeraldsOfTzeentch)
        val player = paid.player1
        val card = paid.putCardInHand(player, name)
        paid.giveColorlessMana(player, 4)
        paid.giveMana(player, Color.BLUE)

        assertNull(paid.castSpell(player, card).error)
        paid.resolveAll()

        assertTrue(paid.permanentsNamed(player, name).contains(card))
        assertEquals(3, paid.projected.getPower(card))
        assertEquals(3, paid.projected.getToughness(card))
        assertEquals(0, paid.manaPool(player).colorless)
        assertEquals(0, paid.manaPool(player).blue)
    }

    @Test
    fun flyingPreventsGroundCreaturesFromBlockingAndDirectEntryDoesNotTriggerCascade() {
        val d = customScenario(HeraldsOfTzeentch, "Grizzly Bears")
        val player = d.player1
        val library = d.zoneNames(player, Zone.LIBRARY)
        val herald = d.putCreatureOnBattlefield(player, name)
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(herald)

        assertEquals(0, d.stackSize)
        assertEquals(library, d.zoneNames(player, Zone.LIBRARY))
        assertEquals(3, d.projected.getPower(herald))
        assertEquals(3, d.projected.getToughness(herald))
        assertTrue(d.projected.hasKeyword(herald, Keyword.FLYING))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(herald), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(d.player2, mapOf(blocker to listOf(herald))).error)
        assertNull(d.declareNoBlockers(d.player2).error)
    }

    @Test
    fun cascadeCastsTheFirstLowerCostNonlandForFreeAndReturnsOtherExilesToTheBottom() {
        val d = customScenario(
            HeraldsOfTzeentch,
            "Forest",
            "Serra Angel",
            "Colossal Dreadmaw",
            "Grizzly Bears",
        )
        val player = d.player1
        val initialLibrary = d.zoneNames(player, Zone.LIBRARY)
        val herald = d.putCardInHand(player, name)
        d.putCardOnTopOfLibrary(player, "Grizzly Bears")
        d.putCardOnTopOfLibrary(player, "Colossal Dreadmaw")
        d.putCardOnTopOfLibrary(player, "Serra Angel")
        d.putCardOnTopOfLibrary(player, "Forest")
        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.BLUE)

        assertNull(d.castSpell(player, herald).error)
        assertEquals(2, d.stackSize)
        assertTrue(d.permanentsNamed(player, name).isEmpty())
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))

        assertTrue(d.permanentsNamed(player, name).contains(herald))
        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isNotEmpty())
        assertTrue(d.permanentsNamed(player, "Serra Angel").isEmpty())
        assertTrue(d.permanentsNamed(player, "Colossal Dreadmaw").isEmpty())
        assertEquals(0, d.manaPool(player).colorless)
        assertEquals(0, d.manaPool(player).blue)
        assertEquals(initialLibrary.size + 3, d.zoneNames(player, Zone.LIBRARY).size)
        assertEquals(
            setOf("Forest", "Serra Angel", "Colossal Dreadmaw"),
            d.zoneNames(player, Zone.LIBRARY).takeLast(3).toSet(),
        )
        assertTrue(d.zoneNames(player, Zone.EXILE).isEmpty())
    }

    @Test
    fun decliningTheCascadeCastReturnsTheEligibleCardToTheBottom() {
        val d = customScenario(HeraldsOfTzeentch, "Grizzly Bears")
        val player = d.player1
        val herald = d.putCardInHand(player, name)
        d.putCardOnTopOfLibrary(player, "Grizzly Bears")
        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.BLUE)

        assertNull(d.castSpell(player, herald).error)
        d.resolveAll(Choices(yes = false))

        assertTrue(d.permanentsNamed(player, name).contains(herald))
        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isEmpty())
        assertEquals("Grizzly Bears", d.zoneNames(player, Zone.LIBRARY).last())
        assertTrue(d.zoneNames(player, Zone.EXILE).isEmpty())
    }

    @Test
    fun cascadeDoesNotCastEqualOrHigherCostNonlandsWhenNoCheaperCardExists() {
        val d = customScenario(HeraldsOfTzeentch, "Forest", "Serra Angel", "Colossal Dreadmaw")
        val player = d.player1
        val herald = d.putCardInHand(player, name)
        d.putCardOnTopOfLibrary(player, "Colossal Dreadmaw")
        d.putCardOnTopOfLibrary(player, "Serra Angel")
        d.putCardOnTopOfLibrary(player, "Forest")
        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.BLUE)

        assertNull(d.castSpell(player, herald).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, name).contains(herald))
        assertTrue(d.permanentsNamed(player, "Serra Angel").isEmpty())
        assertTrue(d.permanentsNamed(player, "Colossal Dreadmaw").isEmpty())
        assertTrue(d.zoneNames(player, Zone.LIBRARY).containsAll(listOf("Forest", "Serra Angel", "Colossal Dreadmaw")))
        assertTrue(d.zoneNames(player, Zone.EXILE).isEmpty())
    }
}
