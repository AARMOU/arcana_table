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

class FlashPhotographyScenarioTest {
    @Test
    fun createsATokenCopyOfTargetPermanent() {
        val d = scenario("Sol Ring")
        val p = d.player1
        val ring = d.putPermanentOnBattlefield(p, "Sol Ring")
        val photo = d.putCardInHand(p, "Flash Photography")
        d.giveMana(p, Color.BLUE); d.giveMana(p, Color.BLUE); d.giveColorlessMana(p, 2)
        assertNull(d.castSpell(p, photo, listOf(ring)).error)
        d.resolveAll()
        assertEquals(2, d.permanentsNamed(p, "Sol Ring").size)
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Flash Photography"))
    }
}
