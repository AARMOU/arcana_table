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

class DisplacedDinosaursScenarioTest {
    @Test
    fun anArtifactYouControlEntersAsA77Dinosaur() {
        val d = scenario("Sol Ring")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Displaced Dinosaurs")
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)
        d.castSpell(p, ring)
        d.resolveAll()
        val onBoard = d.findPermanent(p, "Sol Ring")!!
        assertTrue(d.projected.isCreature(onBoard))
        assertEquals(7, d.projected.getPower(onBoard))
        assertEquals(7, d.projected.getToughness(onBoard))
        assertTrue(d.projected.hasSubtype(onBoard, "Dinosaur"))
    }
}
