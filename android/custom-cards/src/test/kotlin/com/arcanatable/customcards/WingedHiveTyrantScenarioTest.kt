package com.arcanatable.customcards
import com.arcanatable.customcards.cards.WingedHiveTyrant
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.dsl.card
import org.junit.Assert.*
import org.junit.Test
class WingedHiveTyrantScenarioTest {
    @Test fun countersOnAlliedCreatureGrantFlyingAndHaste() {
        val testCounterSpell = card("Test Counter Grant") {
            manaCost = "{G}"
            typeLine = "Sorcery"
            oracleText = "Put a +1/+1 counter on target creature."
            spell {
                val targetCreature = target(TargetFilter.Creature)
                effect = Effects.AddCounters(CounterType.PLUS_ONE_PLUS_ONE, 1, targetCreature)
            }
        }
        val d = customScenario(WingedHiveTyrant, "Grizzly Bears")
        d.registerCard(testCounterSpell)
        val p = d.player1
        val tyrant = d.putCreatureOnBattlefield(p, "Winged Hive Tyrant")
        val ally = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        assertFalse(d.projected.hasKeyword(ally, Keyword.FLYING))
        val spell = d.putCardInHand(p, "Test Counter Grant")
        d.giveMana(p, Color.GREEN)
        assertNull(d.castSpell(p, spell, listOf(ally)).error)
        d.resolveAll()
        assertTrue(d.projected.hasKeyword(ally, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(ally, Keyword.HASTE))
        assertFalse(d.projected.hasKeyword(enemy, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(tyrant, Keyword.FLYING))
    }
}
