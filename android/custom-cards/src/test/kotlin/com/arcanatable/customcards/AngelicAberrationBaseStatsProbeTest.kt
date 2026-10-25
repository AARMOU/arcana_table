package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

/**
 * Angelic Aberration (M3C 39) is NOT registered here.
 *
 * Oracle: sacrifice any number of creatures each with BASE power OR BASE toughness
 * 1 or less, then create that many 4/4 colorless Eldrazi Angel tokens with flying
 * and vigilance. A base-0/2 creature qualifies even though
 * GameObjectFilter.basePowerOrToughness(1) checks only equality to 1.
 *
 * This test intentionally preserves the missing <= 1 base-stat primitive as
 * a failing REAL-ENGINE acceptance case, not an approximate implementation.
 */
class AngelicAberrationBaseStatsProbeTest {
    private val probe = card("Angelic Aberration Base-Stat Probe") {
        manaCost = "{1}"
        typeLine = "Sorcery"
        spell {
            effect = Effects.SacrificeAnyNumber(
                GameObjectFilter.Creature.youControl().basePowerOrToughness(1)
            ) then Effects.CreateToken(
                count = DynamicAmounts.permanentsSacrificedThisWay(),
                power = 4,
                toughness = 4,
                creatureTypes = setOf("Eldrazi", "Angel"),
                keywords = setOf(Keyword.FLYING, Keyword.VIGILANCE)
            )
        }
    }

    private fun castWith(d: GameTestDriver, pick: String) {
        val p = d.player1
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, d.putCardInHand(p, probe.name)).error)
        d.resolveAll(Choices(pick = listOf(pick)))
    }

    @Test fun positiveControlBaseOneCreatureCanBeSacrificedForAngel() {
        val d = customScenario(probe)
        val one = card("Aberration Base-One Creature") {
            manaCost = "{1}"
            typeLine = "Creature — Eldrazi Scion"
            power = 1
            toughness = 2
        }
        d.registerCard(CardLoader.fromJsonPreservingIds(exportLine(one)))
        val victim = d.putCreatureOnBattlefield(d.player1, one.name)

        castWith(d, one.name)

        assertTrue("A creature with base power 1 qualifies", victim in d.state.getZone(d.player1, Zone.GRAVEYARD))
        assertEquals(1, d.permanentsNamed(d.player1, "Eldrazi Angel").size)
    }

    @Test fun zeroBasePowerMustQualifyUnderOracleButExistingEqualityFilterExcludesIt() {
        val d = customScenario(probe)
        val zero = card("Aberration Base-Zero Creature") {
            manaCost = "{1}"
            typeLine = "Creature — Eldrazi Scion"
            power = 0
            toughness = 2
        }
        d.registerCard(CardLoader.fromJsonPreservingIds(exportLine(zero)))
        val victim = d.putCreatureOnBattlefield(d.player1, zero.name)

        castWith(d, zero.name)

        assertTrue("Oracle permits sacrifice of base 0/2, but basePowerOrToughness(1) does not", victim in d.state.getZone(d.player1, Zone.GRAVEYARD))
        assertEquals("Create one Angel for the eligible 0/2 sacrificed", 1, d.permanentsNamed(d.player1, "Eldrazi Angel").size)
    }
}
