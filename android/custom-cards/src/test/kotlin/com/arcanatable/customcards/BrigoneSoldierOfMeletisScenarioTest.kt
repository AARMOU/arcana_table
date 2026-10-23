package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BrigoneSoldierOfMeletis
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import org.junit.Assert.*
import org.junit.Test

class BrigoneSoldierOfMeletisScenarioTest {
    @Test
    fun targetingSpellAddsCounterThatCanBeSpentToDraw() {
        val d = customScenario(BrigoneSoldierOfMeletis, "Giant Growth")
        val p = d.player1
        val brigone = d.putCreatureOnBattlefield(p, "Brigone, Soldier of Meletis")
        val growth = d.putCardInHand(p, "Giant Growth")
        d.giveMana(p, Color.GREEN)

        assertNull(d.castSpell(p, growth, listOf(brigone)).error)
        d.resolveAll()
        assertEquals(1, d.state.getEntity(brigone)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)

        d.removeSummoningSickness(brigone)
        val draw = d.abilityActions(p, brigone).first().action as ActivateAbility
        val handSizeBeforeDraw = d.zoneNames(p, com.wingedsheep.sdk.core.Zone.HAND).size
        assertNull(d.submit(draw).error)
        d.resolveAll()
        assertEquals(handSizeBeforeDraw + 1, d.zoneNames(p, com.wingedsheep.sdk.core.Zone.HAND).size)
        assertEquals(0, d.state.getEntity(brigone)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)
    }
}
