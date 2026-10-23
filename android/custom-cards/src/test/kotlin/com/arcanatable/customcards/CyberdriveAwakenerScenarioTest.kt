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

class CyberdriveAwakenerScenarioTest {
    @Test
    fun noncreatureArtifactsBecomeFlying44s() {
        val d = scenario("Sol Ring")
        val p = d.player1
        val ring = d.putPermanentOnBattlefield(p, "Sol Ring")
        val awakener = d.putCardInHand(p, "Cyberdrive Awakener")
        d.giveMana(p, Color.BLUE); d.giveColorlessMana(p, 5)
        d.castSpell(p, awakener)
        d.resolveAll()
        assertTrue(d.projected.isCreature(ring))
        assertEquals(4, d.projected.getPower(ring))
        assertTrue(d.projected.hasKeyword(ring, Keyword.FLYING))
    }
}
