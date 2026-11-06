package com.arcanatable.customcards

import com.arcanatable.customcards.cards.GoliathTruck
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class GoliathTruckScenarioTest {
    @Test
    fun crewTwoUsesCreaturePowerAndTargetsDifferentAttackerForCounters() {
        val d = customScenario(GoliathTruck, "Grizzly Bears", "Ornithopter")
        val p = d.player1
        val truck = d.putPermanentOnBattlefield(p, "Goliath Truck")
        val crew = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val ally = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val zeroPower = d.putCreatureOnBattlefield(p, "Ornithopter")

        assertFalse(d.projected.isCreature(truck))
        assertNotNull("A zero-power creature cannot crew alone", d.submit(CrewVehicle(p, truck, listOf(zeroPower))).error)
        assertNull(d.submit(CrewVehicle(p, truck, listOf(crew))).error)
        d.resolveAll()
        assertTrue(d.projected.isCreature(truck))
        assertTrue(d.isTapped(crew))

        // Place both creatures on the battlefield before this turn for combat legality.
        d.removeSummoningSickness(truck)
        d.removeSummoningSickness(ally)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(truck, ally), d.player2).error)
        d.resolveAll(Choices(targets = listOf(ally)))

        assertEquals(4, d.projected.getPower(ally))
        assertEquals(4, d.projected.getToughness(ally))
        assertEquals(4, d.projected.getPower(truck))
        assertEquals(4, d.projected.getToughness(truck))
        assertEquals(2, d.projected.getPower(crew))
    }

    @Test
    fun withoutAnotherAttackingCreatureThereIsNoLegalStowageTarget() {
        val d = customScenario(GoliathTruck, "Grizzly Bears")
        val p = d.player1
        val truck = d.putPermanentOnBattlefield(p, "Goliath Truck")
        val crew = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        assertNull(d.submit(CrewVehicle(p, truck, listOf(crew))).error)
        d.resolveAll()
        d.removeSummoningSickness(truck)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(truck), d.player2).error)
        d.resolveAll()

        assertEquals("Vehicle cannot grant itself Stowage counters", 4, d.projected.getPower(truck))
        assertEquals(4, d.projected.getToughness(truck))
    }
}
