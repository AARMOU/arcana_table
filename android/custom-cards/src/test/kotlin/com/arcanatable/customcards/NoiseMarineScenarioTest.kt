package com.arcanatable.customcards
import com.arcanatable.customcards.cards.NoiseMarine
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test
class NoiseMarineScenarioTest {
    @Test fun cascadeSpellCountsBeforeMarineEntersForSonicBlaster() {
        val d = customScenario(NoiseMarine, "Grizzly Bears")
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        val marine = d.putCardInHand(p, "Noise Marine")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, marine).error)
        d.resolveAll(Choices(targets = listOf(d.player2)))
        assertEquals(1, d.permanentsNamed(p, "Grizzly Bears").size)
        assertEquals(18, d.getLifeTotal(d.player2))
    }
}
