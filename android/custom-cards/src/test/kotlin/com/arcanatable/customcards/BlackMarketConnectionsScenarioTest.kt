package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

/** Regression against upstream Argentum's BlackMarketConnections card, not a duplicate Arcana definition. */
class BlackMarketConnectionsScenarioTest {
    @Test
    fun choosesFirstModeAndCreatesTreasureAtPrecombatMain() {
        val d = CardPool.driver("Black Market Connections").apply {
            initMirrorMatch(Deck.of("Forest" to 40))
        }
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Black Market Connections")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        d.bothPass()
        val firstChoice = d.pendingDecision as ChooseOptionDecision
        assertTrue(firstChoice.options.first().contains("Sell Contraband"))
        d.submitDecision(p, OptionChosenResponse(firstChoice.id, 0))

        val finishChoosing = d.pendingDecision as ChooseOptionDecision
        assertEquals("Don't choose a mode", finishChoosing.options.last())
        d.submitDecision(p, OptionChosenResponse(finishChoosing.id, finishChoosing.options.lastIndex))
        d.resolveAll()
        assertEquals(19, d.getLifeTotal(p))
        assertEquals(1, d.getPermanents(p).count { d.getCardName(it) == "Treasure" })
    }
}
