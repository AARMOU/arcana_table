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

    @Test fun removeAllCountersDoesNotRemoveAnUnrelatedPermanentsCounters() {
        val probe = card("Cultist Counter Scope Probe") {
            manaCost = "{B}"
            typeLine = "Sorcery"
            spell {
                val permanent = target(TargetFilter.Permanent)
                effect = Effects.RemoveAllCounters(permanent)
            }
        }
        val d = customScenario(probe, "Grizzly Bears", "Hill Giant")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val untouched = d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        d.addComponent(target, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)))
        d.addComponent(untouched, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 4)))
        val spell = d.putCardInHand(d.player1, "Cultist Counter Scope Probe")
        d.giveMana(d.player1, com.wingedsheep.sdk.core.Color.BLACK)
        assertNull(d.castSpell(d.player1, spell, listOf(target)).error)
        d.resolveAll()
        assertEquals(0, d.state.getEntity(target)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)
        assertEquals(4, d.state.getEntity(untouched)!!.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0)
    }

    /**
     * Probe the SDK's actual next-spell discount with the count captured BEFORE
     * removal. This deliberately targets a permanent only: opponent/player
     * counter removal and the complete Cultist card are separate engine gaps.
     */
    private fun counterDiscountProbe() = card("Cultist Next Spell Discount Probe") {
        manaCost = "{B}"
        typeLine = "Sorcery"
        spell {
            val permanent = target(TargetFilter.Permanent)
            effect = Effects.Pipeline {
                val removedCount = storeNumber(DynamicAmounts.countersOnTarget(null))
                run(Effects.RemoveAllCounters(permanent))
                run(Effects.ReduceNextSpellCost(removedCount.amount))
            }
        }
    }

    @Test fun removedCounterCountDiscountsExactlyNextSpellGenericMana() {
        val d = customScenario(counterDiscountProbe(), "Grizzly Bears", "Hill Giant")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.addComponent(bear, CountersComponent(mapOf(
            CounterType.PLUS_ONE_PLUS_ONE to 2, CounterType.CHARGE to 1
        )))
        val probe = d.putCardInHand(d.player1, "Cultist Next Spell Discount Probe")
        d.giveMana(d.player1, com.wingedsheep.sdk.core.Color.BLACK)
        assertNull(d.castSpell(d.player1, probe, listOf(bear)).error)
        d.resolveAll()
        assertEquals(0, d.state.getEntity(bear)!!.get<CountersComponent>()?.getCount(CounterType.CHARGE) ?: 0)
        val giant = d.putCardInHand(d.player1, "Hill Giant")
        d.giveMana(d.player1, com.wingedsheep.sdk.core.Color.RED)
        assertNull("three counters removed should reduce {3}{R} to {R}",
            d.castSpell(d.player1, giant).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(d.player1, "Hill Giant").size)
    }

    @Test fun discountIsConsumedByFirstMatchingSpellNotAllLaterSpells() {
        val d = customScenario(counterDiscountProbe(), "Grizzly Bears", "Hill Giant")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.addComponent(bear, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)))
        val probe = d.putCardInHand(d.player1, "Cultist Next Spell Discount Probe")
        d.giveMana(d.player1, com.wingedsheep.sdk.core.Color.BLACK)
        assertNull(d.castSpell(d.player1, probe, listOf(bear)).error)
        d.resolveAll()
        val first = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, com.wingedsheep.sdk.core.Color.GREEN)
        assertNull("first creature should consume the one-shot discount", d.castSpell(d.player1, first).error)
        d.resolveAll()
        val second = d.putCardInHand(d.player1, "Hill Giant")
        d.giveMana(d.player1, com.wingedsheep.sdk.core.Color.RED)
        d.giveColorlessMana(d.player1, 1)
        assertNotNull("second spell must still cost {3}{R}", d.castSpell(d.player1, second).error)
        assertTrue(d.permanentsNamed(d.player1, "Hill Giant").isEmpty())
    }

    /**
     * Mutated Cultist needs a cast trigger, not an enters-the-battlefield trigger.
     * Exercise the supported isCast() API independently before combining it with
     * the still-unverified next-spell discount and opponent counter removal.
     */
    private fun castTriggerProbe() = card("Cultist Cast Trigger Probe") {
        manaCost = "{1}{B}"
        typeLine = "Creature — Eldrazi Horror"
        power = 1
        toughness = 3
        triggeredAbility {
            trigger = Triggers.self.isCast()
            effect = Effects.AddColorlessMana(1)
        }
    }

    @Test fun castTriggerFiresWhenCreatureSpellIsCast() {
        val d = customScenario(castTriggerProbe())
        val spell = d.putCardInHand(d.player1, "Cultist Cast Trigger Probe")
        d.giveMana(d.player1, com.wingedsheep.sdk.core.Color.BLACK)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()
        assertEquals("cast trigger resolves even though creature has no ETB trigger",
            1, d.manaPool(d.player1).colorless)
        assertEquals(1, d.permanentsNamed(d.player1, "Cultist Cast Trigger Probe").size)
    }

    @Test fun puttingCreatureDirectlyOntoBattlefieldDoesNotFireCastTrigger() {
        val d = customScenario(castTriggerProbe())
        d.putCreatureOnBattlefield(d.player1, "Cultist Cast Trigger Probe")
        d.resolveAll()
        assertEquals("noncast ETB must not trigger a cast ability",
            0, d.manaPool(d.player1).colorless)
        assertEquals(1, d.permanentsNamed(d.player1, "Cultist Cast Trigger Probe").size)
    }
}
