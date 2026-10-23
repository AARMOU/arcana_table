package com.arcanatable.customcards
import com.arcanatable.customcards.cards.Defile
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test
class DefileScenarioTest {
    @Test fun twoSwampsShrinkAndKillTwoToughnessCreature() {
        val d = customScenario(Defile, "Grizzly Bears")
        val p = d.player1
        repeat(2) { d.putPermanentOnBattlefield(p, "Swamp") }
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(p, "Defile")
        d.giveMana(p, Color.BLACK)
        assertNull(d.castSpell(p, spell, listOf(bear)).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
    }
}
