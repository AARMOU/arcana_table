package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.arcanatable.engine.priority.OfflineHumanAutoPass
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.view.LegalActionEnricher
import com.wingedsheep.engine.legalactions.MeaningfulActionFilter
import com.wingedsheep.engine.view.asPriorityAction
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.EnteredThisTurnComponent
import com.wingedsheep.engine.state.components.player.LifeGainedThisTurnComponent
import com.wingedsheep.engine.state.components.player.LifeGainedAmountThisTurnComponent
import com.wingedsheep.engine.state.components.player.PlayerCitysBlessingComponent
import com.wingedsheep.sdk.core.CounterType
import org.junit.Assert.*
import org.junit.Test

/** Compare the original Build 123 DTO-based verdict with the actual new host's raw-action path. */
class OfflineHumanAutoPassParityTest {
    private fun assertSameVerdict(d: GameTestDriver, label: String) {
        val state = d.state
        val player = state.priorityPlayerId ?: error("$label has no priority holder")
        assertNull("$label must be an actual priority window", state.pendingDecision)
        val enumerator = LegalActionEnumerator(
            d.cardRegistry,
            d.services.manaSolver,
            d.services.costCalculator,
            d.services.predicateEvaluator,
            d.services.conditionEvaluator,
            d.services.turnManager
        )
        val enricher = LegalActionEnricher(d.services.manaSolver, d.cardRegistry)
        val oldDto = enricher.enrich(enumerator.enumerate(state, player, EnumerationMode.FULL), state, player)
            .filterNot {
                it.actionType == "ActivateAbility" && !it.isManaAbility &&
                    it.description.substringAfter(':', "").trim().startsWith("Add ", ignoreCase = true)
            }
        val baseline = MeaningfulActionFilter.shouldAutoPass(
            state = state, playerId = player,
            legalActions = oldDto.map { it.asPriorityAction() }, cardRegistry = d.cardRegistry,
        )
        var newEnumCalls = 0
        val candidate = OfflineHumanAutoPass.shouldPass(
            state = state, player = player, registry = d.cardRegistry,
            enumerateActionsOnly = {
                newEnumCalls++
                enumerator.enumerate(state, player, EnumerationMode.ACTIONS_ONLY)
            }
        )
        assertEquals("$label (stack=${state.stack.size}, oldActions=${oldDto.size})", baseline, candidate)
        val guaranteed = com.wingedsheep.engine.legalactions.MeaningfulActionFilter
            .canAutoPassWithoutEnumerating(state, player)
        val ownStackFastPass = OfflineHumanAutoPass.canPassOwnedStackWithoutEnumerating(state, player, d.cardRegistry)
        assertEquals("$label whether enumeration is skipped", if (guaranteed || ownStackFastPass) 0 else 1, newEnumCalls)
        println("AUTO_PASS_PARITY case=$label stack=${state.stack.size} battlefield=${state.getBattlefield().size} original=$baseline candidate=$candidate fullActions=${oldDto.size} candidateEnumerations=$newEnumCalls")
    }

    @Test(timeout = 180000)
    fun tokenHeavyOwnMainAndEmptyStackEndStepKeepTheSameStop() {
        for ((token, count) in listOf("Food" to 40, "Food" to 200, "Treasure" to 200)) {
            val d = scenario()
            repeat(count) { d.putToken(d.player1, token) }
            assertSameVerdict(d, "main-$token-$count")
            d.passPriorityUntil(Step.END)
            assertSameVerdict(d, "end-empty-$token-$count")
        }
    }

    @Test(timeout = 180000)
    fun ownStackFastPassSkipsFullTokenEnumerationAndPreservesVerdict() {
        for (amount in listOf(40, 200, 600)) {
            val d = scenario("Grizzly Bears")
            repeat(amount) { d.putToken(d.player1, "Food") }
            val creature = d.putCardInHand(d.player1, "Grizzly Bears")
            d.giveMana(d.player1, Color.GREEN, 2)
            assertNull("Casting a creature must be legal", d.castSpell(d.player1, creature).error)
            assertEquals(d.player1, d.state.priorityPlayerId)
            assertTrue(d.state.stack.isNotEmpty())
            assertTrue("Safely skippable own stack with $amount Foods",
                OfflineHumanAutoPass.canPassOwnedStackWithoutEnumerating(d.state, d.player1, d.cardRegistry))
            assertSameVerdict(d, "owned-spell-food-$amount")
        }
    }

    @Test(timeout = 60000)
    fun targetlessStackWithSpellskiteStillPassesOnlyWhenOriginalDoes() {
        val d = scenario("Spellskite", "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Spellskite")
        d.putLandOnBattlefield(d.player2, "Island")
        val spell = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.GREEN, 2)
        assertNull(d.castSpell(d.player1, spell).error)
        assertNull(d.submit(PassPriority(d.player1)).error)
        assertTrue(d.state.stack.isNotEmpty())
        assertSameVerdict(d, "spellskite-targetless-stack")
    }

    @Test(timeout = 60000)
    fun targetedStackWithSpellskiteKeepsTheUsefulResponseWindow() {
        val d = scenario("Spellskite", "Grizzly Bears", "Murder")
        d.putCreatureOnBattlefield(d.player2, "Spellskite")
        val bears = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putLandOnBattlefield(d.player2, "Island")
        val murder = d.putCardInHand(d.player1, "Murder")
        d.giveMana(d.player1, Color.BLACK, 3)
        assertNull(d.castSpell(d.player1, murder, listOf(bears)).error)
        assertNull(d.submit(PassPriority(d.player1)).error)
        assertTrue(d.state.stack.isNotEmpty())
        assertSameVerdict(d, "spellskite-targeted-stack")
    }

    @Test(timeout = 240000)
    fun realOcelotEndStepCascadeNeverChangesPassOrStopDecisions() {
        val d = scenario("Ocelot Pride", "Elspeth, Storm Slayer", "Haliya, Guided by Light", "Panharmonicon",
            "The Jolly Balloon Man", "Grizzly Bears", "Spellskite")
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        repeat(40) { d.putToken(d.player1, "Grizzly Bears") }
        d.putCreatureOnBattlefield(d.player1, "Ocelot Pride")
        d.putToken(d.player1, "Ocelot Pride").also { d.addComponent(it, EnteredThisTurnComponent) }
        d.putCreatureOnBattlefield(d.player1, "Haliya, Guided by Light")
        d.putPermanentOnBattlefield(d.player1, "Panharmonicon")
        d.putPermanentOnBattlefield(d.player1, "Elspeth, Storm Slayer")
            .also { d.addComponent(it, CountersComponent(mapOf(CounterType.LOYALTY to 5))) }
        d.putCreatureOnBattlefield(d.player1, "The Jolly Balloon Man")
        d.putCreatureOnBattlefield(d.player2, "Spellskite")
        d.addComponent(d.player1, PlayerCitysBlessingComponent)
        d.addComponent(d.player1, LifeGainedThisTurnComponent)
        d.addComponent(d.player1, LifeGainedAmountThisTurnComponent(3))
        d.setLifeTotal(d.player1, 40)
        d.setLifeTotal(d.player2, 40)

        val enteredEnd = booleanArrayOf(false)
        var windows = 0
        var stackedWindows = 0
        var decisions = 0
        var iterations = 0
        while (iterations++ < 3000) {
            if (d.state.step == Step.END) enteredEnd[0] = true
            if (enteredEnd[0] && d.state.step != Step.END) break
            if (d.state.gameOver) break
            if (d.state.pendingDecision != null) {
                d.decide()
                decisions++
                continue
            }
            val actor = d.state.priorityPlayerId ?: error("No priority holder during Ocelot cascade")
            if (d.state.stack.isNotEmpty()) stackedWindows++
            assertSameVerdict(d, "ocelot-end-step-window-$windows")
            windows++
            assertNull("PassPriority rejected", d.submit(PassPriority(actor)).error)
        }
        println("AUTO_PASS_CASCADE completed=${enteredEnd[0] && d.state.step != Step.END} windows=$windows stackedWindows=$stackedWindows decisions=$decisions iterations=$iterations finalTokens=${d.getPermanents(d.player1).size}")
        assertTrue("fixture must reach its end step", enteredEnd[0])
        assertTrue("fixture must cover actual triggered stack windows", stackedWindows > 0)
        assertTrue("fixture must exercise multiple priority windows", windows >= 10)
        assertTrue("original/pass-only cascade must complete", d.state.step != Step.END)
    }
}
