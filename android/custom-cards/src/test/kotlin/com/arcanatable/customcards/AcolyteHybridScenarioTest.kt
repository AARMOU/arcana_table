package com.arcanatable.customcards

import com.arcanatable.customcards.cards.AcolyteHybrid
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class AcolyteHybridScenarioTest {
    @Test
    fun attackingDestroysTargetArtifactAndItsControllerDraws() {
        val d = customScenario(AcolyteHybrid, "Sol Ring")
        val attacker = d.putCreatureOnBattlefield(d.player1, "Acolyte Hybrid")
        val ring = d.putPermanentOnBattlefield(d.player2, "Sol Ring")
        d.removeSummoningSickness(attacker)
        val attackingPlayerHandBefore = d.getHandSize(d.player1)
        val artifactControllerHandBefore = d.getHandSize(d.player2)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(attacker), d.player2).error)
        d.resolveAll(Choices(targets = listOf(ring)))

        assertNull("the targeted artifact was destroyed", d.findPermanent(d.player2, "Sol Ring"))
        assertEquals(artifactControllerHandBefore + 1, d.getHandSize(d.player2))
        assertEquals(attackingPlayerHandBefore, d.getHandSize(d.player1))
    }

    @Test
    fun attackWithNoLegalArtifactTargetDoesNotDraw() {
        val d = customScenario(AcolyteHybrid)
        val attacker = d.putCreatureOnBattlefield(d.player1, "Acolyte Hybrid")
        d.removeSummoningSickness(attacker)
        val attackingPlayerHandBefore = d.getHandSize(d.player1)
        val opponentHandBefore = d.getHandSize(d.player2)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(attacker), d.player2).error)
        d.resolveAll()

        assertEquals(attackingPlayerHandBefore, d.getHandSize(d.player1))
        assertEquals(opponentHandBefore, d.getHandSize(d.player2))
    }

    @Test
    fun indestructibleArtifactIsNotDestroyedAndItsControllerDoesNotDraw() {
        val d = customScenario(AcolyteHybrid, "Darksteel Citadel")
        val attacker = d.putCreatureOnBattlefield(d.player1, "Acolyte Hybrid")
        val citadel = d.putPermanentOnBattlefield(d.player2, "Darksteel Citadel")
        d.removeSummoningSickness(attacker)
        val artifactControllerHandBefore = d.getHandSize(d.player2)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(attacker), d.player2).error)
        d.resolveAll(Choices(targets = listOf(citadel)))

        assertNotNull("the indestructible artifact remains on the battlefield", d.findPermanent(d.player2, "Darksteel Citadel"))
        assertEquals(artifactControllerHandBefore, d.getHandSize(d.player2))
    }
}
