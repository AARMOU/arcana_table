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

class IronManBleedingEdgeScenarioTest {
    @Test
    fun copiesTheFirstArtifactSpellEachTurnAsNonlegendary() {
        val d = scenario("Sol Ring")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Iron Man, Bleeding Edge")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        d.castSpell(p, ring)
        d.resolveAll(Choices(yes = true))
        assertEquals(2, d.permanentsNamed(p, "Sol Ring").size)
        val second = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        d.castSpell(p, second)
        d.resolveAll(Choices(yes = true))
        assertEquals("only once each turn", 3, d.permanentsNamed(p, "Sol Ring").size)
    }
}
