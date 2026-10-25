package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import org.junit.Assert.*
import org.junit.Test

/**
 * Engine primitive probe for Mutated Cultist. NOT a card implementation.
 * This isolates whether RemoveAllCounters removes every kind on a permanent.
 * No claim is made about opponent poison counters or cost reduction.
 */
class MutatedCultistEngineProbeTest {
    @Test fun removeAllCountersClearsMultipleKindsFromTargetPermanent() {
        val probe = card("Cultist Counter Removal Probe") {
            manaCost = "{B}"
            typeLine = "Sorcery"
            spell {
                val permanent = target(TargetFilter.Permanent)
                effect = Effects.RemoveAllCounters(permanent)
            }
        }
        val d = customScenario(probe, "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.addComponent(bear, CountersComponent(mapOf(
            CounterType.PLUS_ONE_PLUS_ONE to 3,
            CounterType.CHARGE to 2,
        )))
        val spell = d.putCardInHand(d.player1, "Cultist Counter Removal Probe")
        d.giveMana(d.player1, com.wingedsheep.sdk.core.Color.BLACK)
        assertNull(d.castSpell(d.player1, spell, listOf(bear)).error)
        d.resolveAll()
        val counters = d.state.getEntity(bear)!!.get<CountersComponent>()
        assertEquals(0, counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)
        assertEquals(0, counters?.getCount(CounterType.CHARGE) ?: 0)
    }
}
