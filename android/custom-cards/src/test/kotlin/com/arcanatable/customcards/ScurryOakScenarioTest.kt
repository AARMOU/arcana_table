package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ScurryOak
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.Deck
import org.junit.Assert.*
import org.junit.Test

class ScurryOakScenarioTest {
    @Test
    fun evolveCounterMayCreateSquirrel() {
        val d = CardPool.driver("Grizzly Bears").apply {
            registerCard(ScurryOak)
            initMirrorMatch(Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val oak = d.putCreatureOnBattlefield(p, "Scurry Oak")
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()
        assertEquals(1, d.state.getEntity(oak)!!.get<CountersComponent>()!!.getCount(com.wingedsheep.sdk.core.CounterType.PLUS_ONE_PLUS_ONE))
        assertEquals(1, d.getCreatures(p).count { d.getCardName(it) == "Squirrel Token" })
    }
}
