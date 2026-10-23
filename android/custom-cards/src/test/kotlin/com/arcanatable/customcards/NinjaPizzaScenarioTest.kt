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

class NinjaPizzaScenarioTest {
    @Test
    fun secondMainMakesAFoodThatCanSacrificeForMana() {
        val d = scenario()
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Ninja Pizza")
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()
        val food = d.permanentsNamed(p, "Food").single()
        assertTrue("Food's own ability plus the granted mana ability", d.abilityActions(p, food).size >= 1)
        assertTrue(d.legalActions(p).any { (it.action as? ActivateAbility)?.sourceId == food && it.description.contains("any color") })
    }
}
