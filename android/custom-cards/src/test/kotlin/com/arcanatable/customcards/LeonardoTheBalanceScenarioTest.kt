package com.arcanatable.customcards

import com.arcanatable.customcards.cards.LeonardoTheBalance
import com.arcanatable.customcards.cards.AcornHarvest
import com.arcanatable.customcards.support.CardPool
import com.arcanatable.customcards.support.Choices
import com.arcanatable.customcards.support.resolveAll
import com.arcanatable.customcards.support.putToken
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LeonardoTheBalanceScenarioTest {
    @Test
    fun tokenEntryMayPutCountersOnEachCreature() {
        val d = CardPool.driver("Grizzly Bears", "Forest").apply {
            registerCard(LeonardoTheBalance)
            registerCard(AcornHarvest)
            initMirrorMatch(Deck.of("Plains" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val leo = d.putCreatureOnBattlefield(d.player1, "Leonardo, the Balance")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val harvest = d.putCardInHand(d.player1, "Acorn Harvest")
        d.giveColorlessMana(d.player1, 3)
        d.giveMana(d.player1, com.wingedsheep.sdk.core.Color.GREEN)
        assertNull(d.castSpell(d.player1, harvest).error)
        d.resolveAll(Choices(yes = true))

        assertEquals(1, d.state.getEntity(leo)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)
        assertEquals(1, d.state.getEntity(bear)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)
        assertTrue("at least one created Squirrel received the counter", d.getCreatures(d.player1)
            .filter { d.getCardName(it) == "Squirrel Token" }
            .any { d.state.getEntity(it)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) == 1 })
    }
}
