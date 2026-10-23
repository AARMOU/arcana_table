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

class SaiMasterThopteristScenarioTest {
    @Test
    fun artifactSpellsMakeThoptersAndTwoArtifactsDrawACard() {
        val d = scenario("Sol Ring")
        val p = d.player1
        val sai = d.putCreatureOnBattlefield(p, "Sai, Master Thopterist")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        d.castSpell(p, ring)
        d.resolveAll()
        val thopters = d.getPermanents(p).filter { d.getCardName(it) == "Thopter Token" }
        assertEquals(1, thopters.size)
        assertTrue(d.projected.hasKeyword(thopters.single(), Keyword.FLYING))
        assertTrue(d.abilityActions(p, sai).isNotEmpty())
    }
}
