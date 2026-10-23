package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SifterOfSkulls
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class SifterOfSkullsScenarioTest {
    @Test
    fun friendlyNontokenDeathCreatesOneManaCapableScion() {
        val d = customScenario(SifterOfSkulls, "Grizzly Bears", "Doom Blade")
        val p = d.player1
        val sifter = d.putCreatureOnBattlefield(p, "Sifter of Skulls")
        assertTrue(d.cardRegistry.getCard("Sifter of Skulls")!!.colors.isEmpty())
        assertNotNull(sifter)
        val victim = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val blade = d.putCardInHand(p, "Doom Blade")
        d.giveColorlessMana(p, 1)
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, blade, listOf(victim)).error)
        d.resolveAll()
        val scions = d.permanentsNamed(p, "Eldrazi Scion")
        assertEquals(1, scions.size)
        assertTrue(d.abilityActions(p, scions.single()).isNotEmpty())
    }
}
