package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MichelangeloTheHeart
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.Choices
import com.arcanatable.customcards.support.resolveAll
import com.arcanatable.customcards.support.permanentsNamed
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Test

class MichelangeloTheHeartScenarioTest {
    @Test
    fun secondMainAfterAttackAddsCounterAndFood() {
        val d = CardPool.driver("Grizzly Bears").apply {
            registerCard(MichelangeloTheHeart)
            initMirrorMatch(Deck.of("Forest" to 40))
        }
        val mike = d.putCreatureOnBattlefield(d.player1, "Michelangelo, the Heart")
        val target = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.removeSummoningSickness(mike)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player1)
        d.declareAttackers(d.player1, listOf(mike), d.player2)

        d.passPriorityUntil(Step.POSTCOMBAT_MAIN, activePlayer = d.player1)
        d.resolveAll(Choices(targets = listOf(target)))

        val counters = d.state.getEntity(target)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
        assertEquals(1, counters)
        assertEquals(1, d.permanentsNamed(d.player1, "Food").size)
    }
}
