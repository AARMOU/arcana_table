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

class AncientCopperDragonScenarioTest {
    @Test
    fun combatDamageRollsAD20ForTreasures() {
        val d = scenario()
        val p = d.player1
        val dragon = d.putCreatureOnBattlefield(p, "Ancient Copper Dragon")
        d.removeSummoningSickness(dragon)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(p, listOf(dragon), d.player2)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()
        assertEquals(14, d.getLifeTotal(d.player2))
        val treasures = d.permanentsNamed(p, "Treasure").size
        assertTrue("d20 result was $treasures", treasures in 1..20)
    }
}
