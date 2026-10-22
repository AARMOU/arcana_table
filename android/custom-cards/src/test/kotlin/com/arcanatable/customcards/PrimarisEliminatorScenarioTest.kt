package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PrimarisEliminator
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.ChooseModeDecision
import com.wingedsheep.engine.core.ModesChosenResponse
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class PrimarisEliminatorScenarioTest {
    private fun castAndChoose(d: com.arcanatable.customcards.support.GameTestDriver, mode: Int, targets: List<com.wingedsheep.sdk.model.EntityId>) {
        val p = d.player1
        val card = d.putCardInHand(p, "Primaris Eliminator")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, card).error)
        repeat(80) {
            val decision = d.state.pendingDecision
            when {
                decision is ChooseModeDecision ->
                    assertNull(d.submitDecision(decision.playerId, ModesChosenResponse(decision.id, listOf(mode))).error)
                decision is ChooseOptionDecision ->
                    assertNull(d.submitDecision(decision.playerId, OptionChosenResponse(decision.id, mode)).error)
                decision != null -> d.decide(Choices(targets = targets))
                d.state.stack.isNotEmpty() -> d.bothPass()
                else -> return
            }
        }
        fail("Modal trigger did not settle")
    }

    @Test fun executionerRoundDestroysOnlyTheTargetCreature() {
        val d = customScenario(PrimarisEliminator, "Grizzly Bears")
        val victim = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val other = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        castAndChoose(d, 0, listOf(victim))
        assertFalse(victim in d.getCreatures(d.player2))
        assertTrue(other in d.getCreatures(d.player2))
        assertEquals(3, d.projected.getPower(d.findPermanent(d.player1, "Primaris Eliminator")!!))
    }

    @Test fun hyperfragRoundReducesOnlyTargetPlayersCreaturesForOneTurn() {
        val d = customScenario(PrimarisEliminator, "Hill Giant", "Grizzly Bears")
        val own = d.putCreatureOnBattlefield(d.player1, "Hill Giant")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        castAndChoose(d, 1, listOf(d.player2))
        assertEquals(3, d.projected.getPower(own))
        assertEquals(1, d.projected.getPower(enemy))
        assertEquals(2, d.projected.getToughness(enemy))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertEquals(3, d.projected.getPower(enemy))
    }
}
