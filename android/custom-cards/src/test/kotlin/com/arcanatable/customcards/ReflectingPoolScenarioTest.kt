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

class ReflectingPoolScenarioTest {
    @Test
    fun makesManaYourOtherLandsCouldMake() {
        val d = scenario(filler = "Forest")
        val p = d.player1
        val pool = d.putLandOnBattlefield(p, "Reflecting Pool")
        d.putLandOnBattlefield(p, "Forest")
        val action = d.abilityActions(p, pool).first().action as ActivateAbility
        assertNull(d.submit(action.copy(manaColorChoice = Color.GREEN)).error)
        d.resolveAll(Choices(color = Color.GREEN))
        assertEquals(1, d.manaPool(p).green)
    }
}
