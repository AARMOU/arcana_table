package com.arcanatable.customcards
import com.arcanatable.customcards.cards.SisterHospitaller
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test
class SisterHospitallerScenarioTest {
    @Test fun returnsCreatureAndGainsItsManaValueInLife() {
        val d = customScenario(SisterHospitaller, "Grizzly Bears")
        val p = d.player1
        val corpse = d.putCardInGraveyard(p, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Sister Hospitaller")
        d.giveMana(p, Color.WHITE)
        d.giveMana(p, Color.BLACK)
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll(Choices(targets = listOf(corpse)))
        assertEquals(1, d.permanentsNamed(p, "Grizzly Bears").size)
        assertEquals(22, d.getLifeTotal(p))
    }
}
