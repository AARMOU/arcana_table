package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.arcanatable.engine.ai.OfflineAiPolicy
import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.engine.core.PlayLand
import org.junit.Assert.*
import org.junit.Test

/**
 * Isolated one-flag AI experiment. This never alters OfflineAiPolicy.profile or the shipped AI.
 *
 * Argentum has a dedicated land-drop accounting switch. Establish that it fixes the
 * concrete "last card in hand is a playable land" mistake without giving the candidate
 * any additional AI time. Full win-rate and headless/mobile latency gates live in PR #238.
 */
class AiLandDropCandidateTest {
    @Test(timeout = 60000)
    fun onlyCardInHandIsLandCandidatePlaysItInsteadOfWastingLandDrop() {
        val d = scenario("Forest", filler = "Plains")
        assertEquals(d.player1, d.state.activePlayerId)
        d.getHand(d.player1).toList().forEach(d::moveToGraveyard)
        val forest = d.putCardInHand(d.player1, "Forest")
        assertEquals(1, d.getHandSize(d.player1))
        assertTrue("A land drop must be legal before judging AI", d.legalActions(d.player1)
            .any { it.action is PlayLand })

        val candidate = OfflineAiPolicy.profile.copy(
            id = "arcana-mobile-b123-landdrop-experiment",
            landDropIsNotCardLoss = true,
        )
        val bot = AIPlayer.create(d.cardRegistry, d.player1, candidate)
        val selected = OfflineAiPolicy.withThinkingBurst {
            OfflineAiPolicy.think { bot.chooseAction(d.state) }
        }

        assertTrue("With only Forest in hand, the candidate must play a legal land, chose $selected",
            selected is PlayLand)
        assertEquals("The played land should be the only Forest in hand",
            forest, (selected as PlayLand).cardId)
        assertNull("AI submitted an illegal land play", d.submit(selected).error)
        assertEquals(0, d.getHandSize(d.player1))
    }

    @Test(timeout = 60000)
    fun landDropFlagLeavesMobileThinkingLimitsIntact() {
        val d = scenario("Forest", filler = "Plains")
        val candidate = OfflineAiPolicy.profile.copy(
            id = "arcana-mobile-b123-landdrop-experiment",
            landDropIsNotCardLoss = true,
        )
        assertFalse("Current production opponent must remain the unmodified control",
            OfflineAiPolicy.profile.landDropIsNotCardLoss)
        assertTrue(candidate.landDropIsNotCardLoss)
        assertSame("Use the exact mobile budget policy (no added decision time)",
            OfflineAiPolicy.profile.budgetPolicy, candidate.budgetPolicy)
        assertEquals(OfflineAiPolicy.profile.useMeaningfulFilter, candidate.useMeaningfulFilter)
        val budget = candidate.budgetPolicy.budgetForDecision(d.state, d.player1)
        assertEquals(250L, budget.millis)
        assertEquals(2, budget.allowances.targetCandidates)
        assertEquals(0, budget.allowances.rolloutPlayouts)
    }
}
