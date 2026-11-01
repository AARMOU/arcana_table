package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BeaconOfUnrest
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class BeaconOfUnrestIndependentCertificationTest {
    @Test
    fun returnsACreatureFromYourGraveyardAndShufflesBeaconIntoYourLibrary() {
        val d = customScenario(BeaconOfUnrest, "Grizzly Bears")
        val player = d.player1
        val beacon = d.putCardInHand(player, "Beacon of Unrest")
        val creature = d.putCardInGraveyard(player, "Grizzly Bears")
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK, 2)

        assertNull(d.castSpellWithTargets(player, beacon, listOf(ChosenTarget.Card(creature, player, Zone.GRAVEYARD))).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(creature))
        assertFalse(d.getGraveyard(player).contains(creature))
        assertTrue(d.zoneNames(player, Zone.LIBRARY).contains("Beacon of Unrest"))
        assertFalse(d.getGraveyardCardNames(player).contains("Beacon of Unrest"))
    }

    @Test
    fun returnsAnArtifactFromAnOpponentsGraveyardUnderYourControl() {
        val d = customScenario(BeaconOfUnrest, "Sol Ring")
        val player = d.player1
        val opponent = d.player2
        val beacon = d.putCardInHand(player, "Beacon of Unrest")
        val artifact = d.putCardInGraveyard(opponent, "Sol Ring")
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK, 2)

        assertNull(d.castSpellWithTargets(player, beacon, listOf(ChosenTarget.Card(artifact, opponent, Zone.GRAVEYARD))).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(artifact))
        assertEquals(player, d.projected.getController(artifact))
        assertFalse(d.getPermanents(opponent).contains(artifact))
        assertFalse(d.getGraveyard(opponent).contains(artifact))
        assertTrue(d.zoneNames(player, Zone.LIBRARY).contains("Beacon of Unrest"))
        assertFalse(d.getGraveyardCardNames(player).contains("Beacon of Unrest"))
    }

    @Test
    fun threeGenericAndOnlyOneBlackCannotPayTheCost() {
        val d = customScenario(BeaconOfUnrest, "Grizzly Bears")
        val player = d.player1
        val beacon = d.putCardInHand(player, "Beacon of Unrest")
        val creature = d.putCardInGraveyard(player, "Grizzly Bears")
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK)

        assertNotNull(d.castSpellWithTargets(player, beacon, listOf(ChosenTarget.Card(creature, player, Zone.GRAVEYARD))).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Beacon of Unrest"))
        assertTrue(d.getGraveyard(player).contains(creature))
    }

    @Test
    fun twoGenericAndTwoBlackCannotPayTheCost() {
        val d = customScenario(BeaconOfUnrest, "Grizzly Bears")
        val player = d.player1
        val beacon = d.putCardInHand(player, "Beacon of Unrest")
        val creature = d.putCardInGraveyard(player, "Grizzly Bears")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.BLACK, 2)

        assertNotNull(d.castSpellWithTargets(player, beacon, listOf(ChosenTarget.Card(creature, player, Zone.GRAVEYARD))).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Beacon of Unrest"))
        assertTrue(d.getGraveyard(player).contains(creature))
    }

    @Test
    fun aLandCardInAGraveyardIsNotALegalTarget() {
        val d = customScenario(BeaconOfUnrest, "Plains")
        val player = d.player1
        val beacon = d.putCardInHand(player, "Beacon of Unrest")
        val land = d.putCardInGraveyard(player, "Plains")
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK, 2)

        assertNotNull(d.castSpellWithTargets(player, beacon, listOf(ChosenTarget.Card(land, player, Zone.GRAVEYARD))).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Beacon of Unrest"))
        assertTrue(d.getGraveyard(player).contains(land))
    }

    @Test
    fun theTargetIsRequired() {
        val d = customScenario(BeaconOfUnrest)
        val player = d.player1
        val beacon = d.putCardInHand(player, "Beacon of Unrest")
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK, 2)

        assertNotNull(d.castSpell(player, beacon).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Beacon of Unrest"))
    }

    @Test
    fun sorceryCannotBeCastDuringAnOpponentsTurn() {
        val d = customScenario(BeaconOfUnrest, "Grizzly Bears")
        val player = d.player1
        val beacon = d.putCardInHand(player, "Beacon of Unrest")
        val creature = d.putCardInGraveyard(player, "Grizzly Bears")
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK, 2)

        assertNotNull(d.castSpellWithTargets(player, beacon, listOf(ChosenTarget.Card(creature, player, Zone.GRAVEYARD))).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Beacon of Unrest"))
        assertTrue(d.getGraveyard(player).contains(creature))
    }
}
