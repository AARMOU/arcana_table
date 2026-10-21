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
        assertEquals("$label whether enumeration is skipped", if (guaranteed) 0 else 1, newEnumCalls)
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
}
