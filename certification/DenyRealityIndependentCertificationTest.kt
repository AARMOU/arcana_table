package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DenyReality
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class DenyRealityIndependentCertificationTest {
    private fun GameTestDriver.giveDenyRealityMana(player: EntityId) {
        giveColorlessMana(player, 3)
        giveMana(player, Color.BLUE)
        giveMana(player, Color.BLACK)
    }

    @Test
    fun castingRequiresThreeGenericBlueAndBlackMana() {
        val missingBlue = customScenario(DenyReality, "Sol Ring")
        val blueSpell = missingBlue.putCardInHand(missingBlue.player1, "Deny Reality")
        val blueTarget = missingBlue.putPermanentOnBattlefield(missingBlue.player2, "Sol Ring")
        missingBlue.giveColorlessMana(missingBlue.player1, 3)
        missingBlue.giveMana(missingBlue.player1, Color.BLACK)
        assertNotNull(missingBlue.castSpell(missingBlue.player1, blueSpell, listOf(blueTarget)).error)
        assertTrue(missingBlue.getHand(missingBlue.player1).contains(blueSpell))
        assertEquals(0, missingBlue.stackSize)

        val missingBlack = customScenario(DenyReality, "Sol Ring")
        val blackSpell = missingBlack.putCardInHand(missingBlack.player1, "Deny Reality")
        val blackTarget = missingBlack.putPermanentOnBattlefield(missingBlack.player2, "Sol Ring")
        missingBlack.giveColorlessMana(missingBlack.player1, 3)
        missingBlack.giveMana(missingBlack.player1, Color.BLUE)
        assertNotNull(missingBlack.castSpell(missingBlack.player1, blackSpell, listOf(blackTarget)).error)
        assertTrue(missingBlack.getHand(missingBlack.player1).contains(blackSpell))
        assertEquals(0, missingBlack.stackSize)

        val missingGeneric = customScenario(DenyReality, "Sol Ring")
        val genericSpell = missingGeneric.putCardInHand(missingGeneric.player1, "Deny Reality")
        val genericTarget = missingGeneric.putPermanentOnBattlefield(missingGeneric.player2, "Sol Ring")
        missingGeneric.giveColorlessMana(missingGeneric.player1, 2)
        missingGeneric.giveMana(missingGeneric.player1, Color.BLUE)
        missingGeneric.giveMana(missingGeneric.player1, Color.BLACK)
        assertNotNull(missingGeneric.castSpell(missingGeneric.player1, genericSpell, listOf(genericTarget)).error)
        assertTrue(missingGeneric.getHand(missingGeneric.player1).contains(genericSpell))
        assertEquals(0, missingGeneric.stackSize)
    }

    @Test
    fun cascadeTriggersOnCastSkipsLandsAndManaValueFiveAndCastsTheFirstCheaperNonlandForFree() {
        val d = customScenario(DenyReality, "Forest", "Serra Angel", "Island", "Grizzly Bears", "Sol Ring")
        val player = d.player1
        val target = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(player, "Deny Reality")
        d.putCardOnTopOfLibrary(player, "Grizzly Bears")
        d.putCardOnTopOfLibrary(player, "Island")
        d.putCardOnTopOfLibrary(player, "Serra Angel")
        d.putCardOnTopOfLibrary(player, "Forest")
        d.giveDenyRealityMana(player)

        assertNull(d.castSpell(player, spell, listOf(target)).error)
        assertEquals("Cascade triggers when Deny Reality is cast", 2, d.stackSize)
        d.resolveAll(Choices(yes = true, pick = listOf("Grizzly Bears")))

        assertEquals(1, d.permanentsNamed(player, "Grizzly Bears").size)
        assertTrue(d.zoneNames(d.player2, Zone.HAND).contains("Sol Ring"))
        assertTrue(d.permanentsNamed(d.player2, "Sol Ring").isEmpty())
        assertEquals(setOf("Forest", "Serra Angel", "Island"), d.zoneNames(player, Zone.LIBRARY).takeLast(3).toSet())
        assertTrue(d.zoneNames(player, Zone.EXILE).isEmpty())
        assertEquals(0, d.manaPool(player).colorless)
        assertEquals(0, d.manaPool(player).blue)
        assertEquals(0, d.manaPool(player).black)
    }

    @Test
    fun decliningTheCascadeCastPutsAllExiledCardsOnTheBottom() {
        val d = customScenario(DenyReality, "Forest", "Grizzly Bears", "Sol Ring")
        val player = d.player1
        val target = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(player, "Deny Reality")
        d.putCardOnTopOfLibrary(player, "Grizzly Bears")
        d.putCardOnTopOfLibrary(player, "Forest")
        d.giveDenyRealityMana(player)

        assertNull(d.castSpell(player, spell, listOf(target)).error)
        d.resolveAll(Choices(yes = false))

        assertTrue(d.permanentsNamed(player, "Grizzly Bears").isEmpty())
        assertEquals(setOf("Forest", "Grizzly Bears"), d.zoneNames(player, Zone.LIBRARY).takeLast(2).toSet())
        assertTrue(d.zoneNames(player, Zone.EXILE).isEmpty())
        assertTrue(d.zoneNames(d.player2, Zone.HAND).contains("Sol Ring"))
    }

    @Test
    fun cascadeDoesNotCastLandsOrNonlandsThatCostFiveOrMore() {
        val d = customScenario(DenyReality, "Forest", "Serra Angel", "Island", "Colossal Dreadmaw", "Sol Ring")
        val player = d.player1
        val target = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val spell = d.putCardInHand(player, "Deny Reality")
        d.putCardOnTopOfLibrary(player, "Colossal Dreadmaw")
        d.putCardOnTopOfLibrary(player, "Island")
        d.putCardOnTopOfLibrary(player, "Serra Angel")
        d.putCardOnTopOfLibrary(player, "Forest")
        d.giveDenyRealityMana(player)
        val librarySize = d.zoneNames(player, Zone.LIBRARY).size

        assertNull(d.castSpell(player, spell, listOf(target)).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, "Serra Angel").isEmpty())
        assertTrue(d.permanentsNamed(player, "Colossal Dreadmaw").isEmpty())
        assertEquals(librarySize, d.zoneNames(player, Zone.LIBRARY).size)
        assertTrue(d.zoneNames(player, Zone.LIBRARY).containsAll(listOf("Forest", "Serra Angel", "Island", "Colossal Dreadmaw")))
        assertTrue(d.zoneNames(player, Zone.EXILE).isEmpty())
        assertTrue(d.zoneNames(d.player2, Zone.HAND).contains("Sol Ring"))
    }

    @Test
    fun onlyAPermanentCanBeTargeted() {
        val noTarget = customScenario(DenyReality)
        val spell = noTarget.putCardInHand(noTarget.player1, "Deny Reality")
        noTarget.giveDenyRealityMana(noTarget.player1)
        assertNotNull(noTarget.castSpell(noTarget.player1, spell).error)
        assertTrue(noTarget.getHand(noTarget.player1).contains(spell))
        assertEquals(0, noTarget.stackSize)

        val playerTarget = customScenario(DenyReality)
        val playerSpell = playerTarget.putCardInHand(playerTarget.player1, "Deny Reality")
        playerTarget.giveDenyRealityMana(playerTarget.player1)
        assertNotNull(playerTarget.castSpell(playerTarget.player1, playerSpell, listOf(playerTarget.player2)).error)
        assertTrue(playerTarget.getHand(playerTarget.player1).contains(playerSpell))
        assertEquals(0, playerTarget.stackSize)
    }

    @Test
    fun sorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(DenyReality, "Sol Ring")
        val player = d.player1
        val opponent = d.player2
        val target = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        val spell = d.putCardInHand(player, "Deny Reality")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        d.giveDenyRealityMana(player)

        assertNotNull(d.castSpell(player, spell, listOf(target)).error)
        assertTrue(d.getHand(player).contains(spell))
        assertEquals(0, d.stackSize)
        assertTrue(d.permanentsNamed(opponent, "Sol Ring").contains(target))
    }
}
