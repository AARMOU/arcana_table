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

class SmugglersCopterScenarioTest {
    @Test
    fun crewedCopterLootsWhenItAttacks() {
        val d = scenario("Grizzly Bears")
        val p = d.player1
        val copter = d.putPermanentOnBattlefield(p, "Smuggler's Copter")
        d.removeSummoningSickness(copter)
        val bears = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        assertNull(d.submit(CrewVehicle(p, copter, listOf(bears))).error)
        d.resolveAll()
        assertTrue(d.projected.isCreature(copter))
        val hand = d.getHand(p).size
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        d.declareAttackers(p, listOf(copter), d.player2)
        d.resolveAll(Choices(yes = true))
        assertEquals("drew one, discarded one", hand, d.getHand(p).size)
        assertEquals(1, d.state.getZone(p, Zone.GRAVEYARD).size)
    }
}
