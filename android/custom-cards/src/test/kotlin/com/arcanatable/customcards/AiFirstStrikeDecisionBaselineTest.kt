package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.arcanatable.engine.ai.OfflineAiPolicy
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

/**
 * Milestone A *baseline*, not a strength fix and not an acceptance gate.
 *
 * Real Argentum actions/creatures are used. AI selections are scored and printed without
 * asserting that the CURRENT opponent is correct. Its score is evidence, not a CI pass.
 * The independent first-strike rules oracle IS asserted so a broken rules setup cannot
 * be mistaken for weak AI evaluation.
 */
class AiFirstStrikeDecisionBaselineTest {
    private fun score(id: String, correct: Boolean, legal: Boolean, actual: Any) {
        println("AI_TACTICAL_BASELINE id=$id correct=$correct legal=$legal " +
            "actual=${actual.javaClass.simpleName}")
    }

    @Test(timeout = 60000)
    fun rulesOracleFirstStrikeBlockerKillsOrdinaryBearAndSurvives() {
        val d = scenario("White Knight", "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val knight = d.putCreatureOnBattlefield(d.player2, "White Knight")
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(bear), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(bear to listOf(knight))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertFalse("An ordinary 2/2 must die before hitting a 2/2 first striker",
            bear in d.state.getBattlefield())
        assertTrue("First-strike 2/2 blocker must survive this combat",
            knight in d.state.getBattlefield())
    }

    @Test(timeout = 60000)
    fun baselineAiShouldUseItsFirstStrikeBlocker() {
        val d = scenario("White Knight", "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val knight = d.putCreatureOnBattlefield(d.player2, "White Knight")
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(bear), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        val action = OfflineAiPolicy.withThinkingBurst {
            OfflineAiPolicy.think { OfflineAiPolicy.create(d.cardRegistry, d.player2).chooseAction(d.state) }
        }
        val legal = d.submit(action).error == null
        if (legal) d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        val correct = legal && action is DeclareBlockers &&
            knight in d.state.getBattlefield() && bear !in d.state.getBattlefield()
        score("first-strike-favorable-block", correct, legal, action)
    }

    @Test(timeout = 60000)
    fun baselineAiShouldAvoidAttackingIntoFirstStrikeBlocker() {
        val d = scenario("White Knight", "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "White Knight")
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        val action = OfflineAiPolicy.withThinkingBurst {
            OfflineAiPolicy.think { OfflineAiPolicy.create(d.cardRegistry, d.player1).chooseAction(d.state) }
        }
        val legal = d.submit(action).error == null
        val correct = legal && !d.isTapped(bear)
        score("avoid-first-strike-suicide-attack", correct, legal, action)
    }

    @Test(timeout = 60000)
    fun baselineAiShouldFavorSafeFirstStrikeAttack() {
        val d = scenario("White Knight", "Grizzly Bears")
        val knight = d.putCreatureOnBattlefield(d.player1, "White Knight")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(knight)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        val action = OfflineAiPolicy.withThinkingBurst {
            OfflineAiPolicy.think { OfflineAiPolicy.create(d.cardRegistry, d.player1).chooseAction(d.state) }
        }
        val legal = d.submit(action).error == null
        val correct = legal && action is DeclareAttackers && d.isTapped(knight)
        score("favorable-first-strike-attack", correct, legal, action)
    }
}
