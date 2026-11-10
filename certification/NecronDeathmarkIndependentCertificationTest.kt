package com.arcanatable.customcards

import com.arcanatable.customcards.cards.NecronDeathmark
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class NecronDeathmarkIndependentCertificationTest {
    private fun GameTestDriver.castDeathmark(player: EntityId) {
        val card = putCardInHand(player, "Necron Deathmark")
        giveMana(player, Color.BLACK, 2)
        giveColorlessMana(player, 3)
        assertNull(castSpell(player, card).error)
    }

    @Test
    fun flashAllowsCastingOnAnOpponentsTurnAndTheEntersTriggerDestroysAndMills() {
        val d = customScenario(NecronDeathmark, "Llanowar Elves", "Grizzly Bears", "Shock", "Sol Ring")
        val player = d.player1
        val opponent = d.player2
        val creature = d.putCreatureOnBattlefield(opponent, "Llanowar Elves")
        val milledCards = listOf("Grizzly Bears", "Shock", "Sol Ring")
        milledCards.forEach { d.putCardOnTopOfLibrary(opponent, it) }
        val librarySize = d.zoneNames(opponent, Zone.LIBRARY).size

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, opponent)
        d.passPriority(opponent)
        assertEquals(opponent, d.activePlayer)
        assertEquals(player, d.priorityPlayer)
        d.castDeathmark(player)
        d.resolveAll(Choices(targets = listOf(creature, opponent)))

        val deathmark = d.permanentsNamed(player, "Necron Deathmark").single()
        assertEquals(5, d.projected.getPower(deathmark))
        assertEquals(3, d.projected.getToughness(deathmark))
        assertTrue(d.getGraveyardCardNames(opponent).contains("Llanowar Elves"))
        assertTrue(d.getGraveyardCardNames(opponent).containsAll(milledCards))
        assertEquals(librarySize - 3, d.zoneNames(opponent, Zone.LIBRARY).size)
    }

    @Test
    fun aNoncreaturePermanentCannotBeChosenAsTheOptionalCreatureTarget() {
        val d = customScenario(NecronDeathmark, "Grizzly Bears", "Shock", "Sol Ring")
        val player = d.player1
        val opponent = d.player2
        val ring = d.putPermanentOnBattlefield(opponent, "Sol Ring")
        listOf("Grizzly Bears", "Shock", "Sol Ring").forEach {
            d.putCardOnTopOfLibrary(opponent, it)
        }
        val librarySize = d.zoneNames(opponent, Zone.LIBRARY).size

        d.castDeathmark(player)
        var passes = 0
        while (d.state.pendingDecision !is ChooseTargetsDecision) {
            assertTrue("trigger target choice never appeared", passes++ < 10 && d.state.stack.isNotEmpty())
            d.bothPass()
        }
        val targetDecision = d.state.pendingDecision as ChooseTargetsDecision
        val legalTargets = targetDecision.legalTargets.values.flatten()
        assertTrue(opponent in legalTargets)
        assertFalse(ring in legalTargets)
        d.resolveAll(Choices(targets = listOf(ring, opponent)))

        assertTrue(d.permanentsNamed(opponent, "Sol Ring").contains(ring))
        assertTrue(d.getGraveyardCardNames(opponent).containsAll(listOf("Grizzly Bears", "Shock", "Sol Ring")))
        assertEquals(librarySize - 3, d.zoneNames(opponent, Zone.LIBRARY).size)
    }

    @Test
    fun theTargetPlayerMayBeDeathmarksController() {
        val d = customScenario(NecronDeathmark, "Grizzly Bears", "Shock", "Sol Ring")
        val player = d.player1
        val milledCards = listOf("Grizzly Bears", "Shock", "Sol Ring")
        milledCards.forEach { d.putCardOnTopOfLibrary(player, it) }
        val librarySize = d.zoneNames(player, Zone.LIBRARY).size
        val opponentLibrarySize = d.zoneNames(d.player2, Zone.LIBRARY).size

        d.castDeathmark(player)
        d.resolveAll(Choices(targets = listOf(player)))

        assertTrue(d.getGraveyardCardNames(player).containsAll(milledCards))
        assertEquals(librarySize - 3, d.zoneNames(player, Zone.LIBRARY).size)
        assertEquals(opponentLibrarySize, d.zoneNames(d.player2, Zone.LIBRARY).size)
    }

    @Test
    fun fiveColorlessManaCannotPayTheTwoBlackManaInItsCost() {
        val d = customScenario(NecronDeathmark)
        val player = d.player1
        val card = d.putCardInHand(player, "Necron Deathmark")
        d.giveColorlessMana(player, 5)

        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Necron Deathmark"))
        assertTrue(d.permanentsNamed(player, "Necron Deathmark").isEmpty())
    }

    @Test
    fun oneBlackAndFourColorlessManaCannotPayTheTwoBlackManaInItsCost() {
        val d = customScenario(NecronDeathmark)
        val player = d.player1
        val card = d.putCardInHand(player, "Necron Deathmark")
        d.giveMana(player, Color.BLACK)
        d.giveColorlessMana(player, 4)

        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.zoneNames(player, Zone.HAND).contains("Necron Deathmark"))
        assertTrue(d.permanentsNamed(player, "Necron Deathmark").isEmpty())
    }
}
