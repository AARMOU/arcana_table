package com.arcanatable.customcards
import com.arcanatable.customcards.cards.TheirNameIsDeath
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test
class TheirNameIsDeathScenarioTest {
    @Test fun destroyNonartifactButSpareArtifactCreatures() {
        val d = customScenario(TheirNameIsDeath, "Grizzly Bears", "Ornithopter")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Ornithopter")
        val spell = d.putCardInHand(p, "Their Name Is Death")
        repeat(3) { d.giveMana(p, Color.BLACK) }
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        assertEquals(1, d.permanentsNamed(p, "Ornithopter").size)
    }
}
