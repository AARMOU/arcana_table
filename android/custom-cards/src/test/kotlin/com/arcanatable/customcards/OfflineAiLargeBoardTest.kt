package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.arcanatable.engine.ai.OfflineAiPolicy
import com.wingedsheep.engine.core.DeclareAttackers
import com.wingedsheep.engine.core.DeclareBlockers
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

/** The app's actual mobile profile, on the same engine and card data as its offline host. */
class OfflineAiLargeBoardTest {
    @Test(timeout = 60000)
    fun manyAffordableTokenAbilitiesDoNotCauseUnboundedSimulation() {
        val d = scenario()
        repeat(140) { d.putToken(d.player1, "Clue") }
        d.giveColorlessMana(d.player1, 100)
        val count = d.legalActions(d.player1).count { it.actionType == "ActivateAbility" && it.affordable }
        assertTrue("fixture offers a large candidate set, got $count", count >= 100)
        val bot = OfflineAiPolicy.create(d.cardRegistry, d.player1)
        val started = System.nanoTime()
        val action = bot.chooseAction(d.state)
        val ms = (System.nanoTime() - started) / 1_000_000
        println("[mobile-ai] token-abilities=$count permanents=${d.state.getBattlefield().size} ms=$ms action=${action.javaClass.simpleName}")
        assertTrue("large candidate search returned in ${ms}ms", ms < 5000)
        assertNull(d.submit(action).error)
    }

    @Test(timeout = 60000)
    fun crowdedBlockingReturnsLegalAssignments() {
        val d = scenario("Grizzly Bears")
        val attackers = List(140) {
            d.putToken(d.player1, "Grizzly Bears").also(d::removeSummoningSickness)
        }
        repeat(140) { d.putToken(d.player2, "Grizzly Bears") }
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.submit(DeclareAttackers(d.player1, attackers.associateWith { d.player2 })).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        val bot = OfflineAiPolicy.create(d.cardRegistry, d.player2)
        val started = System.nanoTime()
        val action = bot.chooseAction(d.state)
        val ms = (System.nanoTime() - started) / 1_000_000
        println("[mobile-ai] blockers permanents=${d.state.getBattlefield().size} ms=$ms action=${action.javaClass.simpleName}")
        assertTrue(action is DeclareBlockers)
        assertTrue("large blocking search returned in ${ms}ms", ms < 5000)
        assertNull(d.submit(action).error)
    }

    @Test(timeout = 60000)
    fun crowdedCombatReturnsALegalDeclaration() {
        for (perSide in listOf(70, 140)) {
            val d = scenario("Grizzly Bears")
            repeat(perSide) {
                d.putToken(d.player1, "Grizzly Bears").also(d::removeSummoningSickness)
                d.putToken(d.player2, "Grizzly Bears").also(d::removeSummoningSickness)
            }
            d.passPriorityUntil(Step.DECLARE_ATTACKERS)
            val bot = OfflineAiPolicy.create(d.cardRegistry, d.player1)
            val started = System.nanoTime()
            val action = bot.chooseAction(d.state)
            val ms = (System.nanoTime() - started) / 1_000_000
            println("[mobile-ai] combat permanents=${d.state.getBattlefield().size} ms=$ms action=${action.javaClass.simpleName}")
            assertTrue(action is DeclareAttackers)
            assertTrue("$perSide creatures per side returned in ${ms}ms", ms < 5000)
            assertNull("fallback must be a legal engine action", d.submit(action).error)
        }
    }

    @Test(timeout = 60000)
    fun crowdedTargetSearchReturnsALegalActionAndKeepsProgressing() {
        val d = scenario("Grizzly Bears", "Murder")
        repeat(140) {
            d.putToken(d.player1, "Grizzly Bears")
            d.putToken(d.player2, "Grizzly Bears")
        }
        d.putCardInHand(d.player1, "Murder")
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 1)
        val bot = OfflineAiPolicy.create(d.cardRegistry, d.player1)
        val budget = OfflineAiPolicy.budgetForDecision(d.state, d.player1)
        assertTrue("crowded profile has a finite decision deadline", budget.millis <= 150)
        assertFalse("crowded boards skip target simulation", budget.allowances.refineTargetsBySimulation)
        val started = System.nanoTime()
        val action = bot.chooseAction(d.state)
        val ms = (System.nanoTime() - started) / 1_000_000
        println("[mobile-ai] targeted permanents=${d.state.getBattlefield().size} ms=$ms action=${action.javaClass.simpleName}")
        assertTrue("large target search returned in ${ms}ms", ms < 5000)
        assertNull(d.submit(action).error)
        d.resolveAll()
        assertNull(d.state.pendingDecision)
    }
}
