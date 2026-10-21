package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.arcanatable.engine.ai.OfflineAiPolicy
import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import org.junit.Assert.*
import org.junit.Test

/**
 * Pinned Build 123 rules + mobile AI, reproducing the expensive alternating priority windows
 * after a large batch of real triggers. No trigger, token, stack item or AI decision is skipped.
 *
 * The control case uses explicit passes only because this particular opponent has no resources
 * and the full mobile AI is required to choose PassPriority on every opponent window.
 * A future non-pass AI action fails the parity test rather than being silently overridden.
 */
class AiEndStepCascadeProfileTest {
    private data class Result(
        val windows: Int,
        val aiChoices: Int,
        val peakStack: Int,
        val life: Int,
        val food: Int,
        val aiMs: Long,
        val resolutionMs: Long,
    )

    private fun cascade(withAi: Boolean, foodCreated: Int = 200): Result {
        val d = scenario("Haliya, Guided by Light")
        val spellName = "Synthetic End Step Food Cascade"
        d.registerCard(card(spellName) {
            manaCost = "{0}"
            colorIdentity = ""
            typeLine = "Instant"
            oracleText = "TEST ONLY: create Food tokens at instant speed"
            spell { effect = Effects.CreateFood(foodCreated) }
        })
        d.setLifeTotal(d.player1, 1000)
        d.setLifeTotal(d.player2, 1000)
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.END)
        d.putCreatureOnBattlefield(d.player1, "Haliya, Guided by Light")
        repeat(200) { d.putToken(d.player1, "Food") }
        val spell = d.putCardInHand(d.player1, spellName)
        assertNull(d.castSpell(d.player1, spell).error)

        val ai = OfflineAiPolicy.create(d.cardRegistry, d.player2)
        var windows = 0
        var aiChoices = 0
        var aiNanos = 0L
        var resolutionNanos = 0L
        var peakStack = 0
        val startNanos = System.nanoTime()
        OfflineAiPolicy.withThinkingBurst {
            while (d.state.stack.isNotEmpty() || d.state.pendingDecision != null) {
                assertTrue("Engine failed to finish the cascade", windows < foodCreated * 8 + 120)
                peakStack = maxOf(peakStack, d.state.stack.size)
                if (d.state.pendingDecision != null) {
                    val t0 = System.nanoTime()
                    d.decide()
                    resolutionNanos += System.nanoTime() - t0
                    continue
                }
                val actor = d.state.priorityPlayerId ?: error("No player has priority")
                val action = if (withAi && actor == d.player2) {
                    aiChoices++
                    val t0 = System.nanoTime()
                    val chosen = ai.chooseAction(d.state)
                    aiNanos += System.nanoTime() - t0
                    assertTrue(
                        "Control opponent unexpectedly found a real response: ${chosen.javaClass.simpleName}",
                        chosen is PassPriority,
                    )
                    chosen
                } else PassPriority(actor)
                val t0 = System.nanoTime()
                assertNull(d.submit(action).error)
                resolutionNanos += System.nanoTime() - t0
                windows++
            }
        }
        val endNanos = System.nanoTime()
        val foodCount = d.getPermanents(d.player1).count { d.getCardName(it) == "Food" }
        val life = d.getLifeTotal(d.player1)
        println(
            "AI_ENDSTEP_PROFILE ai=$withAi windows=$windows aiChoices=$aiChoices " +
                "peakStack=$peakStack aiMs=${aiNanos / 1_000_000} " +
                "resolutionMs=${resolutionNanos / 1_000_000} " +
                "wallMs=${(endNanos - startNanos) / 1_000_000} " +
                "food=$foodCount life=$life"
        )
        assertEquals("All 200 created Food tokens must remain", 200 + foodCreated, foodCount)
        assertEquals("Haliya must register each separate Food ETB", 1000 + foodCreated, life)
        assertTrue("Must exercise a long stack", peakStack >= 100)
        assertTrue("Must complete every priority window", windows >= foodCreated * 2)
        return Result(windows, aiChoices, peakStack, life, foodCount, aiNanos / 1_000_000, resolutionNanos / 1_000_000)
    }

    @Test(timeout = 120000)
    fun aiProcessorWithoutUnusedUndoPreservesExactRulesTransition() {
        val d = scenario("Haliya, Guided by Light")
        val name = "Synthetic Undo Parity Food Cascade"
        d.registerCard(card(name) {
            manaCost = "{0}"
            colorIdentity = ""
            typeLine = "Instant"
            oracleText = "TEST ONLY"
            spell { effect = Effects.CreateFood(20) }
        })
        d.putCreatureOnBattlefield(d.player1, "Haliya, Guided by Light")
        val spell = d.putCardInHand(d.player1, name)
        assertNull(d.castSpell(d.player1, spell).error)
        val originalProcessor = ActionProcessor(d.services)
        val aiProcessor = ActionProcessor(d.services, computeUndo = false)
        var compared = 0
        while (d.state.stack.isNotEmpty() && compared < 28) {
            if (d.state.pendingDecision != null) { d.decide(); continue }
            val priority = d.state.priorityPlayerId ?: error("No priority")
            val action = PassPriority(priority)
            val entry = d.state
            val baseline = originalProcessor.process(entry, action).result
            val candidate = aiProcessor.process(entry, action).result
            assertEquals("No difference in rules-engine error", baseline.error, candidate.error)
            assertEquals("No difference in events", baseline.events, candidate.events)
            assertEquals("No difference in immutable game state", baseline.state, candidate.state)
            assertNull(d.submit(action).error)
            compared++
        }
        assertTrue("Must compare both players and actual stack resolution", compared >= 20)
    }

    @Test(timeout = 900000)
    fun mobileAiPreservesPassOnlyControlOutcomeThroughTwoHundredTriggers() {
        val control = cascade(withAi = false)
        val mobileAi = cascade(withAi = true)
        assertEquals(control.windows, mobileAi.windows)
        assertEquals(control.life, mobileAi.life)
        assertEquals(control.food, mobileAi.food)
        assertEquals(control.peakStack, mobileAi.peakStack)
        assertTrue("Expected repeated AI priority decisions", mobileAi.aiChoices >= 100)
    }
}
