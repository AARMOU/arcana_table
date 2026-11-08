package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HideousTaskmaster
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class HideousTaskmasterIndependentCertificationTest {
    private val name = "Hideous Taskmaster"

    @Test
    fun sixGenericAndOneRedCastItAndDevoidLeavesItColorless() {
        val d = customScenario(HideousTaskmaster)
        val player = d.player1
        val taskmaster = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 6)

        assertNotNull(d.castSpell(player, taskmaster).error)
        assertTrue(d.getHand(player).contains(taskmaster))

        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, taskmaster).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(taskmaster))
        assertEquals(7, d.projected.getPower(taskmaster))
        assertEquals(2, d.projected.getToughness(taskmaster))
        assertTrue("Devoid leaves the creature colorless", d.projected.getColors(taskmaster).isEmpty())
        assertTrue(d.projected.hasKeyword(taskmaster, Keyword.TRAMPLE))
        assertTrue(d.projected.hasKeyword(taskmaster, Keyword.HASTE))
    }

    @Test
    fun fiveGenericManaAndOneRedCannotPayTheCastingCost() {
        val d = customScenario(HideousTaskmaster)
        val player = d.player1
        val taskmaster = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 5)
        d.giveMana(player, Color.RED)

        assertNotNull(d.castSpell(player, taskmaster).error)

        assertTrue(d.getHand(player).contains(taskmaster))
        assertTrue(d.getPermanents(player).isEmpty())
    }

    @Test
    fun castingTakesOneCreatureFromEachOpponentUntapsThemAndGrantsKeywordsUntilEndOfTurn() {
        val d = customScenario(HideousTaskmaster, "Grizzly Bears", "Sol Ring")
        val players = d.initMultiplayer(decks = List(3) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        val player = players[0]
        val opponent = players[1]
        val otherOpponent = players[2]
        val borrowed = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val otherBorrowed = d.putCreatureOnBattlefield(otherOpponent, "Grizzly Bears")
        d.tapPermanent(borrowed)
        d.tapPermanent(otherBorrowed)
        d.putPermanentOnBattlefield(opponent, "Sol Ring")
        val taskmaster = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 6)
        d.giveMana(player, Color.RED)

        assertNull(d.castSpell(player, taskmaster).error)
        d.resolveAll(Choices(targets = listOf(borrowed, otherBorrowed)))

        for (creature in listOf(borrowed, otherBorrowed)) {
            assertEquals(player, d.projected.getController(creature))
            assertFalse(d.isTapped(creature))
            assertTrue(d.projected.hasKeyword(creature, Keyword.TRAMPLE))
            assertTrue(d.projected.hasKeyword(creature, Keyword.HASTE))
        }

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = player)
        assertNull(d.declareAttackers(player, listOf(borrowed), opponent).error)
        d.resolveAll()
        assertTrue(d.zoneNames(opponent, Zone.GRAVEYARD).contains("Sol Ring"))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertEquals(opponent, d.projected.getController(borrowed))
        assertEquals(otherOpponent, d.projected.getController(otherBorrowed))
        for (creature in listOf(borrowed, otherBorrowed)) {
            assertFalse(d.projected.hasKeyword(creature, Keyword.TRAMPLE))
            assertFalse(d.projected.hasKeyword(creature, Keyword.HASTE))
        }
    }

    @Test
    fun castTriggerCannotTakeYourCreatureOrAnOpponentsNoncreature() {
        val d = customScenario(HideousTaskmaster, "Grizzly Bears", "Sol Ring")
        val player = d.player1
        val opponent = d.player2
        val ownCreature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val opposingArtifact = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        val taskmaster = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 6)
        d.giveMana(player, Color.RED)

        assertNull(d.castSpell(player, taskmaster).error)
        d.resolveAll()

        assertEquals(player, d.projected.getController(ownCreature))
        assertEquals(opponent, d.projected.getController(opposingArtifact))
    }

    @Test
    fun puttingHideousTaskmasterOntoTheBattlefieldDoesNotTriggerItsCastAbility() {
        val d = customScenario(HideousTaskmaster, "Grizzly Bears")
        val opponent = d.player2
        val target = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")

        d.putCreatureOnBattlefield(d.player1, name)

        assertEquals(opponent, d.projected.getController(target))
        assertFalse(d.projected.hasKeyword(target, Keyword.TRAMPLE))
        assertFalse(d.projected.hasKeyword(target, Keyword.HASTE))
    }

    @Test
    fun taskmasterAttacksWithHasteAnnihilatorAndTrample() {
        val d = customScenario(HideousTaskmaster, "Llanowar Elves", "Plains")
        val player = d.player1
        val opponent = d.player2
        val taskmaster = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 6)
        d.giveMana(player, Color.RED)

        assertNull(d.castSpell(player, taskmaster).error)
        d.resolveAll()

        val blocker = d.putCreatureOnBattlefield(opponent, "Llanowar Elves")
        val land = d.putLandOnBattlefield(opponent, "Plains")
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(taskmaster), opponent).error)
        d.resolveAll(Choices(pick = listOf("Plains")))

        assertFalse(d.getPermanents(opponent).contains(land))
        assertTrue(d.getPermanents(opponent).contains(blocker))

        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, mapOf(blocker to listOf(taskmaster))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)

        assertEquals(14, d.getLifeTotal(opponent))
        assertFalse(d.getPermanents(opponent).contains(blocker))
    }
}
