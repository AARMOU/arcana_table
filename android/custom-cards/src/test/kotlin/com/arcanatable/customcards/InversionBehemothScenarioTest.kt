package com.arcanatable.customcards

import com.arcanatable.customcards.cards.InversionBehemoth
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class InversionBehemothScenarioTest {
    private fun setup(): GameTestDriver = customScenario(InversionBehemoth, "Giant Spider", "Craw Wurm")

    private fun GameTestDriver.stats(id: EntityId) = projected.getPower(id) to projected.getToughness(id)

    /** Advances to the active player's beginning of combat and returns the trigger's target decision. */
    private fun GameTestDriver.combatTrigger(): ChooseTargetsDecision {
        passPriorityUntil(Step.BEGIN_COMBAT)
        val decision = state.pendingDecision as? ChooseTargetsDecision
        assertNotNull("beginning-of-combat trigger asks for targets", decision)
        return decision!!
    }

    private fun GameTestDriver.chooseAndResolve(decision: ChooseTargetsDecision, targets: List<EntityId>) {
        assertNull(submitTargetSelection(decision.playerId, targets).error)
        resolveAll()
    }

    @Test fun zeroTargetsIsLegalAndChangesNothing() {
        val d = setup()
        val behemoth = d.putCreatureOnBattlefield(d.player1, "Inversion Behemoth")
        val spider = d.putCreatureOnBattlefield(d.player1, "Giant Spider")
        val decision = d.combatTrigger()
        assertEquals(0, decision.targetRequirements.single().minTargets)
        d.chooseAndResolve(decision, emptyList())
        assertEquals(2 to 9, d.stats(behemoth))
        assertEquals(2 to 4, d.stats(spider))
    }

    @Test fun switchesEachChosenCreatureOfAnyController() {
        val d = setup()
        val behemoth = d.putCreatureOnBattlefield(d.player1, "Inversion Behemoth")
        val mine = d.putCreatureOnBattlefield(d.player1, "Giant Spider")
        val theirs = d.putCreatureOnBattlefield(d.player2, "Craw Wurm")
        val untouched = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        d.chooseAndResolve(d.combatTrigger(), listOf(behemoth, mine, theirs))
        assertEquals(9 to 2, d.stats(behemoth))
        assertEquals(4 to 2, d.stats(mine))
        assertEquals("opponent's creature can be targeted", 4 to 6, d.stats(theirs))
        assertEquals("unchosen creature unchanged", 2 to 4, d.stats(untouched))
    }

    @Test fun moreThanNinetyNineTargetsHaveNoCeiling() {
        val d = setup()
        d.putCreatureOnBattlefield(d.player1, "Inversion Behemoth")
        val spiders = (1..105).map { d.putCreatureOnBattlefield(if (it % 2 == 0) d.player1 else d.player2, "Giant Spider") }
        val decision = d.combatTrigger()
        assertTrue("all 105 spiders are legal targets", decision.legalTargets[0].orEmpty().containsAll(spiders))
        d.chooseAndResolve(decision, spiders)
        spiders.forEach { assertEquals(4 to 2, d.stats(it)) }
    }

    @Test fun switchEndsAtEndOfTurn() {
        val d = setup()
        d.putCreatureOnBattlefield(d.player1, "Inversion Behemoth")
        val spider = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        d.chooseAndResolve(d.combatTrigger(), listOf(spider))
        assertEquals(4 to 2, d.stats(spider))
        d.passPriorityUntil(Step.UPKEEP, d.player2)
        assertEquals(2 to 4, d.stats(spider))
    }

    @Test fun noTriggerOnOpponentsTurn() {
        val d = setup()
        d.putCreatureOnBattlefield(d.player2, "Inversion Behemoth")
        d.passPriorityUntil(Step.BEGIN_COMBAT)
        assertNull("opponent's Behemoth does not trigger on player1's turn", d.state.pendingDecision)
        assertTrue(d.state.stack.isEmpty())
    }
}
