package com.arcanatable.customcards

import com.arcanatable.customcards.cards.HexmarkDestroyer
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class HexmarkDestroyerIndependentCertificationTest {
    private val name = "Hexmark Destroyer"

    @Test
    fun castingRequiresFourGenericAndTwoBlackAndEntersAsAArtifactNecronSixSix() {
        val d = customScenario(HexmarkDestroyer)
        val player = d.player1
        val card = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 3)
        d.giveMana(player, Color.BLACK, 2)

        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.getHand(player).contains(card))

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, name).contains(card))
        assertTrue(d.projected.hasType(card, "ARTIFACT"))
        assertTrue(d.projected.isCreature(card))
        assertTrue(d.projected.hasSubtype(card, "NECRON"))
        assertEquals(6, d.projected.getPower(card))
        assertEquals(6, d.projected.getToughness(card))
    }

    @Test
    fun fiveCreaturesCannotBlockButSixCreaturesCan() {
        val d = customScenario(HexmarkDestroyer, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val hexmark = d.putCreatureOnBattlefield(player, name)
        val blockers = List(6) { d.putCreatureOnBattlefield(opponent, "Grizzly Bears") }
        d.removeSummoningSickness(hexmark)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(hexmark), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(opponent, blockers.take(5).associateWith { listOf(hexmark) }).error)
        assertNull(d.declareBlockers(opponent, blockers.associateWith { listOf(hexmark) }).error)
    }

    @Test
    fun unearthReturnsTheCardGrantsHasteAndExilesItAtTheNextEndStep() {
        val d = customScenario(HexmarkDestroyer)
        val player = d.player1
        val opponent = d.player2
        val hexmark = d.putCardInGraveyard(player, name)
        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.BLACK, 2)

        assertNull(d.activate(player, hexmark).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(player, name).contains(hexmark))
        assertFalse(d.getGraveyardCardNames(player).contains(name))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(hexmark), opponent).error)
        d.passPriorityUntil(Step.END)
        d.resolveAll()

        assertTrue(d.zoneNames(player, Zone.EXILE).contains(name))
        assertFalse(d.permanentsNamed(player, name).contains(hexmark))
        assertFalse(d.getGraveyardCardNames(player).contains(name))
    }

    @Test
    fun unearthExilesTheCardIfItWouldLeaveTheBattlefield() {
        val d = customScenario(HexmarkDestroyer, "Murder")
        val player = d.player1
        val hexmark = d.putCardInGraveyard(player, name)
        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.BLACK, 2)
        assertNull(d.activate(player, hexmark).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(player, name).contains(hexmark))

        val murder = d.putCardInHand(player, "Murder")
        d.giveColorlessMana(player, 1)
        d.giveMana(player, Color.BLACK, 2)
        assertNull(d.castSpell(player, murder, listOf(hexmark)).error)
        d.resolveAll()

        assertTrue(d.zoneNames(player, Zone.EXILE).contains(name))
        assertFalse(d.permanentsNamed(player, name).contains(hexmark))
        assertFalse(d.getGraveyardCardNames(player).contains(name))
    }

    @Test
    fun unearthCannotBeActivatedWithLessThanFourGenericAndTwoBlackMana() {
        val d = customScenario(HexmarkDestroyer)
        val player = d.player1
        val hexmark = d.putCardInGraveyard(player, name)
        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.BLACK)

        assertNotNull(d.activate(player, hexmark).error)
        assertTrue(d.getGraveyardCardNames(player).contains(name))
    }

    @Test
    fun unearthCannotBeActivatedFromTheHand() {
        val d = customScenario(HexmarkDestroyer)
        val player = d.player1
        val hexmark = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.BLACK, 2)

        assertNotNull(d.activate(player, hexmark).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains(name))
    }

    @Test
    fun onlyItsControllerCanUnearthTheCardFromTheGraveyard() {
        val d = customScenario(HexmarkDestroyer)
        val owner = d.player1
        val opponent = d.player2
        val hexmark = d.putCardInGraveyard(owner, name)
        d.giveColorlessMana(opponent, 4)
        d.giveMana(opponent, Color.BLACK, 2)
        assertNull(d.passPriority(owner).error)
        assertEquals(opponent, d.priorityPlayer)

        assertNotNull(d.activate(opponent, hexmark).error)
        assertTrue(d.getGraveyardCardNames(owner).contains(name))
    }

    @Test
    fun unearthCannotBeActivatedDuringAnOpponentsTurn() {
        val d = customScenario(HexmarkDestroyer)
        val player = d.player1
        val opponent = d.player2
        val hexmark = d.putCardInGraveyard(player, name)
        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.BLACK, 2)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        assertEquals(player, d.priorityPlayer)

        assertNotNull(d.activate(player, hexmark).error)
        assertTrue(d.getGraveyardCardNames(player).contains(name))
    }

    @Test
    fun unearthCannotBeActivatedWhileTheStackIsNotEmpty() {
        val d = customScenario(HexmarkDestroyer, "Shock")
        val player = d.player1
        val hexmark = d.putCardInGraveyard(player, name)
        val shock = d.putCardInHand(player, "Shock")
        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, shock, listOf(d.player2)).error)
        assertEquals(1, d.stackSize)

        d.giveColorlessMana(player, 4)
        d.giveMana(player, Color.BLACK, 2)
        assertNotNull(d.activate(player, hexmark).error)
        assertTrue(d.getGraveyardCardNames(player).contains(name))
    }
}
