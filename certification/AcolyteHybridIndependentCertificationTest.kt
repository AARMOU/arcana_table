package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AcolyteHybrid
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class AcolyteHybridIndependentCertificationTest {
    private fun GameTestDriver.attackAcolyte(acolyte: EntityId) {
        removeSummoningSickness(acolyte)
        passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(declareAttackers(player1, listOf(acolyte), player2).error)
    }

    @Test
    fun castingRequiresTwoGenericAndOneRedAndEntersAsATwoTwo() {
        val d = customScenario(AcolyteHybrid)
        val player = d.player1
        val hybrid = d.putCardInHand(player, "Acolyte Hybrid")
        d.giveColorlessMana(player, 1)

        assertNotNull(d.castSpell(player, hybrid).error)
        d.giveMana(player, Color.RED)
        assertNotNull(d.castSpell(player, hybrid).error)
        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, hybrid).error)
        d.resolveAll()

        assertTrue(d.getPermanents(player).contains(hybrid))
        assertEquals(2, d.projected.getPower(hybrid))
        assertEquals(2, d.projected.getToughness(hybrid))
    }

    @Test
    fun attackingDestroysAtMostOneTargetArtifactAndItsControllerDraws() {
        val d = customScenario(AcolyteHybrid, "Sol Ring")
        val attacker = d.putCreatureOnBattlefield(d.player1, "Acolyte Hybrid")
        val target = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val attackerHand = d.getHandSize(d.player1)
        val artifactControllerHand = d.getHandSize(d.player2)

        d.attackAcolyte(attacker)
        d.resolveAll(Choices(targets = listOf(target)))

        assertFalse(d.getPermanents(d.player2).contains(target))
        assertEquals(1, d.permanentsNamed(d.player2, "Sol Ring").size)
        assertEquals("The artifact's controller draws exactly one card", artifactControllerHand + 1, d.getHandSize(d.player2))
        assertEquals(attackerHand, d.getHandSize(d.player1))
    }

    @Test
    fun anArtifactIsTheOnlyLegalTarget() {
        val d = customScenario(AcolyteHybrid, "Sol Ring", "Grizzly Bears")
        val attacker = d.putCreatureOnBattlefield(d.player1, "Acolyte Hybrid")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val creature = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val opponentHand = d.getHandSize(d.player2)

        d.attackAcolyte(attacker)
        d.resolveAll(Choices(targets = listOf(creature)))

        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertTrue(d.getPermanents(d.player2).contains(creature))
        assertEquals(opponentHand, d.getHandSize(d.player2))
    }

    @Test
    fun mayChooseNoArtifactAndThenNothingIsDestroyedOrDrawn() {
        val d = customScenario(AcolyteHybrid, "Sol Ring")
        val attacker = d.putCreatureOnBattlefield(d.player1, "Acolyte Hybrid")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val opponentHand = d.getHandSize(d.player2)

        d.attackAcolyte(attacker)
        d.resolveAll(Choices(targets = emptyList()))

        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertEquals(opponentHand, d.getHandSize(d.player2))
    }

    @Test
    fun theAbilityDoesNotTriggerWhenAcolyteHybridDoesNotAttack() {
        val d = customScenario(AcolyteHybrid, "Sol Ring", "Grizzly Bears")
        val attacker = d.putCreatureOnBattlefield(d.player1, "Acolyte Hybrid")
        val otherAttacker = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val artifact = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        val playerHand = d.getHandSize(d.player1)
        val opponentHand = d.getHandSize(d.player2)
        d.removeSummoningSickness(attacker)
        d.removeSummoningSickness(otherAttacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(d.player1, listOf(otherAttacker), d.player2).error)
        d.resolveAll()

        assertTrue(d.getPermanents(d.player2).contains(artifact))
        assertEquals(playerHand, d.getHandSize(d.player1))
        assertEquals(opponentHand, d.getHandSize(d.player2))
    }
}
