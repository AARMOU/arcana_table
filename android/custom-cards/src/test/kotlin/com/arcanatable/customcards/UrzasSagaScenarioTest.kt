package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import org.junit.Assert.*
import org.junit.Test

class UrzasSagaScenarioTest {
    @Test
    fun chapterOneTapsForColorlessAndChapterThreeFetchesASmallArtifact() {
        val d = scenario("Sol Ring")
        val p = d.player1
        val saga = d.putCardInHand(p, "Urza's Saga")
        d.playLand(p, saga)
        d.resolveAll()
        val onBoard = d.findPermanent(p, "Urza's Saga")!!
        assertTrue("chapter I grants a mana ability", d.abilityActions(p, onBoard).isNotEmpty())
        d.putCardOnTopOfLibrary(p, "Sol Ring")
        repeat(4) { d.putCardOnTopOfLibrary(p, "Plains") } // keep it out of the next draws
        // Two more of your turns: chapter II, then chapter III.
        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        d.resolveAll()
        assertEquals("chapter II adds the Construct ability", 2, d.abilityActions(p, onBoard).size)
        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        d.resolveAll(Choices(pick = listOf("Sol Ring")))
        assertEquals(1, d.permanentsNamed(p, "Sol Ring").size)
        assertNull("sacrificed after chapter III", d.findPermanent(p, "Urza's Saga"))
    }
}
