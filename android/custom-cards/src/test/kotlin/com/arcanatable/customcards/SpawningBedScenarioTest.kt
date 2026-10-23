package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SpawningBed
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SpawningBedScenarioTest {
    @Test
    fun ordinaryManaAbilityAddsOneColorless() {
        val d = customScenario(SpawningBed)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Spawning Bed")
        assertNull(d.activate(p, land, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun sacrificesLandAsCostAndCreatesThreeManaCapableOneOneScions() {
        val d = customScenario(SpawningBed)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Spawning Bed")
        d.giveColorlessMana(p, 6)
        assertNull(d.activate(p, land, index = 1).error)
        assertNull("land must leave the battlefield as a cost", d.findPermanent(p, "Spawning Bed"))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Spawning Bed"))
        d.resolveAll()
        val scions = d.permanentsNamed(p, "Eldrazi Scion")
        assertEquals(3, scions.size)
        assertTrue(scions.all { d.abilityActions(p, it).isNotEmpty() })
        val scion = scions.first()
        val manaAction = d.abilityActions(p, scion).first().action as ActivateAbility
        assertNull(d.submit(manaAction).error)
        d.resolveAll()
        assertEquals(2, d.permanentsNamed(p, "Eldrazi Scion").size)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun cannotActivateSpawnAbilityWithoutSixMana() {
        val d = customScenario(SpawningBed)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Spawning Bed")
        assertNotNull(d.activate(p, land, index = 1).error)
        assertNotNull(d.findPermanent(p, "Spawning Bed"))
    }
}
