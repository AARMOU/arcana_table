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

class FoodChainScenarioTest {
    @Test
    fun exilingACreatureAddsOnePlusItsManaValueForCreatureSpells() {
        val d = scenario("Craw Wurm")
        val p = d.player1
        val chain = d.putPermanentOnBattlefield(p, "Food Chain")
        val wurm = d.putCreatureOnBattlefield(p, "Craw Wurm")
        val action = d.abilityActions(p, chain).first().action as ActivateAbility
        val result = d.submit(action.copy(costPayment = AdditionalCostPayment(exiledCards = listOf(wurm)), manaColorChoice = Color.GREEN))
        assertNull(result.error)
        d.resolveAll(Choices(color = Color.GREEN))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Craw Wurm"))
        val pool = d.manaPool(p)
        val total = pool.green + pool.restrictedMana.size
        assertEquals("Craw Wurm has mana value 6", 7, total)
    }
}
