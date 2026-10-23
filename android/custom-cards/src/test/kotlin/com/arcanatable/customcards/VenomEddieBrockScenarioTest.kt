package com.arcanatable.customcards

import com.arcanatable.customcards.cards.VenomEddieBrock
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.resolveAll
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Test

class VenomEddieBrockScenarioTest {
    @Test
    fun villainDiesAndTriggersVenomCounterAndDraw() {
        val d = CardPool.driver("Powerful Broker", "Murder").apply {
            registerCard(VenomEddieBrock)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val venom = d.putCreatureOnBattlefield(d.player1, "Venom, Eddie Brock")
        d.putCreatureOnBattlefield(d.player2, "Powerful Broker")
        val handBefore = d.getHandSize(d.player1)
        val murder = d.putCardInHand(d.player1, "Murder")
        d.giveMana(d.player1, Color.BLACK, 2)
        d.giveColorlessMana(d.player1, 1)
        assertEquals(null, d.castSpell(d.player1, murder, listOf(d.findPermanent(d.player2, "Powerful Broker")!!)).error)
        d.resolveAll()

        val counters = d.state.getEntity(venom)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
        assertEquals(1, counters)
        assertEquals(handBefore + 1, d.getHandSize(d.player1))
        assertEquals(null, d.findPermanent(d.player2, "Powerful Broker"))
    }
}
