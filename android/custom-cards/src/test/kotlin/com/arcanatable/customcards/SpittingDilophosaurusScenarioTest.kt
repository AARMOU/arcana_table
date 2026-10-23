package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SpittingDilophosaurus
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.Choices
import com.arcanatable.customcards.support.resolveAll
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpittingDilophosaurusScenarioTest {
    @Test
    fun entersWithTargetedCounterAndPreventsThatCreatureFromBlocking() {
        val d = CardPool.driver("Grizzly Bears").apply {
            registerCard(SpittingDilophosaurus)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val dilophosaurus = d.putCardInHand(p, "Spitting Dilophosaurus")
        val blocker = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val attackTarget = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, dilophosaurus).error)
        d.resolveAll(Choices(targets = listOf(blocker)))

        assertEquals(
            1,
            d.state.getEntity(blocker)!!.get<CountersComponent>()!!
                .getCount(CounterType.MINUS_ONE_MINUS_ONE),
        )

        d.removeSummoningSickness(dilophosaurus)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(dilophosaurus), d.player2).error)
        d.resolveAll(Choices(targets = listOf(attackTarget)))
        assertEquals(
            1,
            d.state.getEntity(attackTarget)!!.get<CountersComponent>()!!
                .getCount(CounterType.MINUS_ONE_MINUS_ONE),
        )
        d.passPriorityUntil(Step.DECLARE_BLOCKERS, activePlayer = p)
        assertNotNull(d.declareBlockers(d.player2, mapOf(dilophosaurus to listOf(blocker))).error)
        d.declareNoBlockers(d.player2)
        d.resolveAll()
    }
}
