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

/** Regression against upstream Argentum's JaheiraFriendOfTheForest card, not a duplicate Arcana definition. */
class JaheiraFriendOfTheForestScenarioTest {
    @Test
    fun tokensYouControlTapForGreen() {
        val d = scenario("Jaheira, Friend of the Forest")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Jaheira, Friend of the Forest")
        val treasure = d.putToken(p, "Treasure")
        val nontoken = d.putPermanentOnBattlefield(p, "Plains")
        val actions = d.abilityActions(p, treasure)
        assertTrue("granted {T}: Add {G} plus the Treasure's own ability", actions.size >= 2)
        assertTrue(d.abilityActions(p, nontoken).none { it.description.contains("{G}") })
    }
}
