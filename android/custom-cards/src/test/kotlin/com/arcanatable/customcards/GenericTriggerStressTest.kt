package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.arcanatable.engine.priority.OfflineHumanAutoPass
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.legalactions.MeaningfulActionFilter
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.engine.view.asPriorityAction
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.effects.Effect
import org.junit.Assert.*
import org.junit.Test

/**
 * Engine-wide event/priority stress tests. Synthetic spells are TEST-ONLY; no shipped card,
 * engine JAR or production client is changed.
 *
 * Distinguishes one life gain of N from N distinct life gains, and N artifact/creature ETBs.
 * Samples FULL+enriched vs Build 125's production fast-pass every 40 priority windows.
 */
class GenericTriggerStressTest {
    private fun testSpell(name: String, effect: Effect) = card(name) {
        manaCost = "{0}"
        colorIdentity = ""
        typeLine = "Sorcery"
        oracleText = "TEST ONLY: generic triggered-event stress fixture"
        spell { this.effect = effect }
    }

    private data class Stats(
        val windows: Int,
        val pendingDecisions: Int,
        val peakStack: Int,
        val fullEnumerations: Int,
        val fastPasses: Int,
        val fullEnumerateMs: Long,
        val candidateMs: Long,
        val resolutionMs: Long,
        val p1Life: Int,
        val p2Life: Int,
    )

    private fun runCase(
        label: String,
        effect: Effect,
        watcher: String?,
        expectedLifeGain: Int,
        expectedOpponentLoss: Int,
        expectedCreated: Int,
        threshold: Int = 200,
    ): Stats {
        val fixture = "Synthetic $label"
        val driver = scenario(*(listOfNotNull(watcher).toTypedArray()))
        driver.registerCard(testSpell(fixture, effect))
        driver.setLifeTotal(driver.player1, 1000)
        driver.setLifeTotal(driver.player2, 1000)
        if (watcher != null) driver.putCreatureOnBattlefield(driver.player1, watcher)
        val boardBefore = driver.getPermanents(driver.player1).size
        val spell = driver.putCardInHand(driver.player1, fixture)
        val cast = driver.castSpell(driver.player1, spell)
        assertNull("$label must cast successfully", cast.error)
        assertTrue("$label must put a spell on stack", driver.state.stack.isNotEmpty())

        val enumerator = LegalActionEnumerator(
            driver.cardRegistry, driver.services.manaSolver, driver.services.costCalculator,
            driver.services.predicateEvaluator, driver.services.conditionEvaluator,
            driver.services.turnManager,
        )
        val enricher = LegalActionEnricher(driver.services.manaSolver, driver.cardRegistry)
        var windows = 0
        var pending = 0
        var peak = 0
        var fullCalls = 0
        var fast = 0
        var baselineNanos = 0L
        var candidateNanos = 0L
        var resolutionNanos = 0L
        val maxWindows = threshold * 7 + 400

        while (driver.state.stack.isNotEmpty() || driver.state.pendingDecision != null) {
            assertTrue("$label exceeded $maxWindows priority windows", windows < maxWindows)
            assertFalse("$label must not unexpectedly end the game", driver.state.gameOver)
            peak = maxOf(peak, driver.state.stack.size)
            if (driver.state.pendingDecision != null) {
                pending++
                driver.decide()
                continue
            }
            val state = driver.state
            val seat = state.priorityPlayerId ?: error("$label missing priority")
            // The candidate's real AUTO path must preserve baseline verdicts, not only the
            // resulting board. Sample across the entire backlog without forcing thousands of
            // FULL menu builds in the benchmark itself.
            if (windows < 6 || windows % 40 == 0) {
                val t0 = System.nanoTime()
                val original = enricher.enrich(
                    enumerator.enumerate(state, seat, EnumerationMode.FULL), state, seat
                ).filterNot {
                    it.actionType == "ActivateAbility" && !it.isManaAbility &&
                        it.description.substringAfter(':', "").trim().startsWith("Add ", ignoreCase = true)
                }
                val expected = MeaningfulActionFilter.shouldAutoPass(
                    state, seat, original.map { it.asPriorityAction() },
                    cardRegistry = driver.cardRegistry,
                )
                baselineNanos += System.nanoTime() - t0
                fullCalls++
                var calls = 0
                val t1 = System.nanoTime()
                val actual = OfflineHumanAutoPass.shouldPass(
                    state, seat, driver.cardRegistry, enumerateActionsOnly = {
                        calls++
                        enumerator.enumerate(state, seat, EnumerationMode.ACTIONS_ONLY)
                    }
                )
                candidateNanos += System.nanoTime() - t1
                if (calls == 0) fast++
                assertEquals("$label priority=$windows stack=${state.stack.size} seat=$seat",
                    expected, actual)
            }
            val t2 = System.nanoTime()
            assertNull("$label rejected pass at window $windows",
                driver.submit(PassPriority(seat)).error)
            resolutionNanos += System.nanoTime() - t2
            windows++
        }

        val life1 = driver.getLifeTotal(driver.player1)
        val life2 = driver.getLifeTotal(driver.player2)
        assertEquals("$label exact life gain", 1000 + expectedLifeGain, life1)
        assertEquals("$label exact opponent losses", 1000 - expectedOpponentLoss, life2)
        assertEquals("$label exact token production", expectedCreated, driver.getPermanents(driver.player1).size - boardBefore)
        println("GENERIC_STRESS_RESULT case=$label windows=$windows peakStack=$peak decisions=$pending fullCalls=$fullCalls "+
            "fastPassSamples=$fast fullMs=${baselineNanos/1_000_000} candidateMs=${candidateNanos/1_000_000} "+
            "resolutionMs=${resolutionNanos/1_000_000} life=$life1 opponentLife=$life2 created=$expectedCreated")
        assertTrue("$label must have observed priority windows", windows >= 2)
        return Stats(windows,pending,peak,fullCalls,fast,baselineNanos/1_000_000,candidateNanos/1_000_000,
            resolutionNanos/1_000_000,life1,life2)
    }

    @Test(timeout = 180000)
    fun oneHugeLifeGainIsNotTwoHundredIndividualTriggers() {
        val result = runCase(
            "gain200once", Effects.GainLife(200), "Dina, Soul Steeper",
            expectedLifeGain = 200, expectedOpponentLoss = 1, expectedCreated = 0)
        assertTrue("One life-gain event should create a tiny stack", result.peakStack < 8)
    }

    @Test(timeout = 300000)
    fun twoHundredSeparateLifeGainsWithoutCreatures() {
        val result = runCase(
            "gain1x200", Effects.Repeat(DynamicAmounts.fixed(200), Effects.GainLife(1)),
            "Dina, Soul Steeper",
            expectedLifeGain = 200, expectedOpponentLoss = 200, expectedCreated = 0)
        assertTrue("Separate life-gain events should produce a large stack", result.peakStack >= 50)
    }

    @Test(timeout = 300000)
    fun twoHundredArtifactEtbsAreRealHaliyaTriggers() {
        val result = runCase(
            "food200", Effects.CreateFood(200), "Haliya, Guided by Light",
            expectedLifeGain = 200, expectedOpponentLoss = 0, expectedCreated = 200)
        assertTrue(result.peakStack >= 50)
    }

    @Test(timeout = 300000)
    fun twoHundredCreatureEtbsAreRealHaliyaTriggers() {
        val result = runCase(
            "creature200", Effects.CreateToken(1,1,setOf(Color.WHITE),setOf("Soldier"),count=200),
            "Haliya, Guided by Light",
            expectedLifeGain = 200, expectedOpponentLoss = 0, expectedCreated = 200)
        assertTrue(result.peakStack >= 50)
    }
}
