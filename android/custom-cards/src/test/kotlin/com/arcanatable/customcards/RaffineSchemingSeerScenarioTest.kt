package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RaffineSchemingSeer
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.Choices
import com.arcanatable.customcards.support.resolveAll
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RaffineSchemingSeerScenarioTest {
    @Test
    fun attackingWithTwoCreaturesLetsTargetConniveTwice() {
        val d = CardPool.driver("Grizzly Bears", "Forest").apply {
            registerCard(RaffineSchemingSeer)
            initMirrorMatch(Deck.of("Plains" to 40))
        }
        val raffine = d.putCreatureOnBattlefield(d.player1, "Raffine, Scheming Seer")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.putCardInHand(d.player1, "Grizzly Bears")
        d.putCardInHand(d.player1, "Grizzly Bears")
        d.removeSummoningSickness(raffine)
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(raffine, bear), d.player2).error)

        d.resolveAll(Choices(targets = listOf(bear), pick = listOf("Grizzly Bears")))

        assertEquals(
            "Connive X placed two counters on the chosen attacker",
            2,
            d.state.getEntity(bear)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0,
        )
    }
}
