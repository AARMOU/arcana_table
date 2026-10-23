package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PrimalAmulet
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class PrimalAmuletScenarioTest {
    @Test
    fun castingInstantAddsChargeCounter() {
        val d = CardPool.driver("Ranger's Guile", "Grizzly Bears").apply {
            registerCard(PrimalAmulet)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val amulet = d.putPermanentOnBattlefield(p, "Primal Amulet")
        val bear = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Ranger's Guile")
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, spell, listOf(bear)).error)
        d.resolveAll()
        assertEquals(1, d.state.getEntity(amulet)!!.get<CountersComponent>()!!.getCount(CounterType.CHARGE))
    }
}
