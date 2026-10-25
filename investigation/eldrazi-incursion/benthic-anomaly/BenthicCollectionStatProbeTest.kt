package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import org.junit.Assert.*
import org.junit.Test

/** Primitive probe, NOT an implemented Benthic Anomaly or registration candidate. */
class BenthicCollectionStatProbeTest {
    private fun fixture(loop: Boolean) = card("Benthic Collection Stat Probe") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        spell {
            effect = Effects.Pipeline {
                val selected = gather(GameObjectFilter.Creature, player = Player.Each)
                if (loop) {
                    val total = storeNumber(DynamicAmount.Fixed(0))
                    run(Effects.ForEachInCollection(selected,
                        Effects.StoreNumber(total.key, total.amount + DynamicAmounts.powerOf(EffectTarget.IterationEntity))))
                    run(Effects.AddColorlessMana(total.amount))
                } else {
                    val total = storeNumber(DynamicAmounts.powerOf(selected.asTarget))
                    run(Effects.AddColorlessMana(total.amount))
                }
            }
        }
    }
    @Test fun directStoredProjectedPowerIsReadableWithoutALoop() {
        val d = customScenario(fixture(false), "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val spell = d.putCardInHand(d.player1, "Benthic Collection Stat Probe")
        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()
        assertEquals(2, d.manaPool(d.player1).colorless)
    }
    @Test fun chosenCollectionPowerAccumulatorMustSurviveEachIterationAndReturnToThePipeline() {
        val d = customScenario(fixture(true), "Grizzly Bears", "Hill Giant")
        d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Hill Giant")
        val spell = d.putCardInHand(d.player1, "Benthic Collection Stat Probe")
        assertNull(d.castSpell(d.player1, spell).error)
        d.resolveAll()
        assertEquals("Collection has 2 + 3 power; zero means loop outputs were lost", 5, d.manaPool(d.player1).colorless)
    }
}
