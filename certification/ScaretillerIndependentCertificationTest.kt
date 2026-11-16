package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Scaretiller
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class ScaretillerIndependentCertificationTest {
    private val name = "Scaretiller"

    private fun GameTestDriver.attackCreature(player: EntityId, defender: EntityId, creature: EntityId) {
        removeSummoningSickness(creature)
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(declareAttackers(player, listOf(creature), defender).error)
    }

    private fun GameTestDriver.resolveScaretillerTrigger(
        player: EntityId,
        mode: Int,
        choices: Choices = Choices()
    ) {
        var passes = 0
        while (state.pendingDecision !is ChooseOptionDecision) {
            assertTrue(
                "Scaretiller's trigger did not offer a mode",
                passes++ < 10 && state.stack.isNotEmpty()
            )
            bothPass()
        }
        val decision = state.pendingDecision as ChooseOptionDecision
        assertEquals("Scaretiller's controller chooses the mode", player, decision.playerId)
        assertEquals(2, decision.options.size)
        assertNull(submitDecision(player, OptionChosenResponse(decision.id, mode)).error)
        resolveAll(choices)
    }

    @Test
    fun castingCostsFourGenericAndScaretillerIsAnArtifactCreatureOneFour() {
        val d = customScenario(Scaretiller)
        val p = d.player1
        val card = d.putCardInHand(p, name)
        d.giveColorlessMana(p, 3)

        assertNotNull(d.castSpell(p, card).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains(name))
        assertEquals(3, d.manaPool(p).colorless)

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()

        assertTrue(d.permanentsNamed(p, name).contains(card))
        assertEquals(1, d.projected.getPower(card))
        assertEquals(4, d.projected.getToughness(card))
        assertTrue(d.projected.hasType(card, "ARTIFACT"))
        assertTrue(d.projected.hasType(card, "CREATURE"))
        assertEquals(0, d.manaPool(p).colorless)
    }

    @Test
    fun attackingScaretillerPutsAChosenLandFromHandOntoTheBattlefieldTapped() {
        val d = customScenario(Scaretiller, "Plains")
        val p = d.player1
        val scaretiller = d.putCreatureOnBattlefield(p, name)
        val land = d.putCardInHand(p, "Plains")
        d.attackCreature(p, d.player2, scaretiller)

        assertTrue(d.isTapped(scaretiller))
        d.resolveScaretillerTrigger(p, mode = 0, choices = Choices(pick = listOf("Plains")))

        assertTrue(d.getPermanents(p).contains(land))
        assertTrue(d.isTapped(land))
        assertFalse(d.zoneNames(p, Zone.HAND).contains("Plains"))
    }

    @Test
    fun theHandLandModeMayBeDeclined() {
        val d = customScenario(Scaretiller, "Plains")
        val p = d.player1
        val scaretiller = d.putCreatureOnBattlefield(p, name)
        val land = d.putCardInHand(p, "Plains")
        d.attackCreature(p, d.player2, scaretiller)
        d.resolveScaretillerTrigger(p, mode = 0, choices = Choices(yes = false))

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Plains"))
        assertFalse(d.getPermanents(p).contains(land))
        assertTrue(d.isTapped(scaretiller))
    }

    @Test
    fun graveyardModeReturnsYourTargetLandOntoTheBattlefieldTapped() {
        val d = customScenario(Scaretiller, "Plains")
        val p = d.player1
        val scaretiller = d.putCreatureOnBattlefield(p, name)
        val land = d.putCardInGraveyard(p, "Plains")
        d.attackCreature(p, d.player2, scaretiller)
        d.resolveScaretillerTrigger(p, mode = 1, choices = Choices(targets = listOf(land)))

        assertTrue(d.getPermanents(p).contains(land))
        assertTrue(d.isTapped(land))
        assertFalse(d.zoneNames(p, Zone.GRAVEYARD).contains("Plains"))
    }

    @Test
    fun aNonlandCardInHandIsNotPutOntoTheBattlefield() {
        val d = customScenario(Scaretiller, "Grizzly Bears")
        val p = d.player1
        val scaretiller = d.putCreatureOnBattlefield(p, name)
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.attackCreature(p, d.player2, scaretiller)
        d.resolveScaretillerTrigger(p, mode = 0, choices = Choices(pick = listOf("Grizzly Bears")))

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Grizzly Bears"))
        assertFalse(d.getPermanents(p).contains(bear))
        assertTrue(d.isTapped(scaretiller))
    }

    @Test
    fun aNonlandCardInYourGraveyardIsNotAReturnableTarget() {
        val d = customScenario(Scaretiller, "Plains", "Grizzly Bears")
        val p = d.player1
        val scaretiller = d.putCreatureOnBattlefield(p, name)
        val land = d.putCardInGraveyard(p, "Plains")
        val bear = d.putCardInGraveyard(p, "Grizzly Bears")
        d.attackCreature(p, d.player2, scaretiller)
        d.resolveScaretillerTrigger(p, mode = 1, choices = Choices(targets = listOf(bear)))

        assertTrue(d.getPermanents(p).contains(land))
        assertTrue(d.isTapped(land))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertFalse(d.getPermanents(p).contains(bear))
    }

    @Test
    fun anOpponentsGraveyardLandIsNotAReturnableTarget() {
        val d = customScenario(Scaretiller, "Plains")
        val p = d.player1
        val scaretiller = d.putCreatureOnBattlefield(p, name)
        val yourLand = d.putCardInGraveyard(p, "Plains")
        val opponentsLand = d.putCardInGraveyard(d.player2, "Plains")
        d.attackCreature(p, d.player2, scaretiller)
        d.resolveScaretillerTrigger(p, mode = 1, choices = Choices(targets = listOf(opponentsLand)))

        assertTrue(d.getPermanents(p).contains(yourLand))
        assertTrue(d.isTapped(yourLand))
        assertTrue(d.getGraveyard(d.player2).contains(opponentsLand))
        assertFalse(d.getPermanents(p).contains(opponentsLand))
    }

    @Test
    fun anotherCreatureBecomingTappedDoesNotTriggerScaretiller() {
        val d = customScenario(Scaretiller, "Plains", "Grizzly Bears")
        val p = d.player1
        val scaretiller = d.putCreatureOnBattlefield(p, name)
        val otherCreature = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val land = d.putCardInHand(p, "Plains")
        d.attackCreature(p, d.player2, otherCreature)
        d.resolveAll()

        assertTrue(d.isTapped(otherCreature))
        assertFalse(d.isTapped(scaretiller))
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Plains"))
        assertFalse(d.getPermanents(p).contains(land))
    }
}
