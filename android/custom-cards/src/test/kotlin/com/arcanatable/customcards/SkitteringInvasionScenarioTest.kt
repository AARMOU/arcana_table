package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SkitteringInvasion
import com.arcanatable.customcards.support.*
import org.junit.Assert.*
import org.junit.Test

class SkitteringInvasionScenarioTest {
    @Test
    fun createsFiveManaCapableEldraziSpawn() {
        val d = customScenario(SkitteringInvasion)
        val p = d.player1
        val spell = d.putCardInHand(p, "Skittering Invasion")
        d.giveColorlessMana(p, 7)

        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()

        val spawn = d.permanentsNamed(p, "Eldrazi Spawn")
        assertEquals(5, spawn.size)
        assertTrue(spawn.all { d.abilityActions(p, it).isNotEmpty() })
    }
}
