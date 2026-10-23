package com.arcanatable.customcards

import com.arcanatable.customcards.cards.StonecoilSerpent
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import org.junit.Assert.*
import org.junit.Test

class StonecoilSerpentScenarioTest {
    @Test
    fun castXCreatesExactlyThatManyPlusOneCountersAndPrintedCombatKeywords() {
        val d = customScenario(StonecoilSerpent)
        val p = d.player1
        val card = d.putCardInHand(p, "Stonecoil Serpent")
        d.giveColorlessMana(p, 4)
        assertNull(d.castXSpell(p, card, 4).error)
        d.resolveAll()
        val serpent = d.permanentsNamed(p, "Stonecoil Serpent").single()
        assertEquals(4, d.projected.getPower(serpent))
        assertEquals(4, d.projected.getToughness(serpent))
        assertTrue(d.projected.hasKeyword(serpent, Keyword.REACH))
        assertTrue(d.projected.hasKeyword(serpent, Keyword.TRAMPLE))
    }

    @Test
    fun fullProtectionRejectsMulticoloredTargetingButNotMonocoloredTargeting() {
        val d = customScenario(StonecoilSerpent)
        d.registerCard(card("Two-Color Scenario Strike") {
            manaCost = "{R}{G}"
            colorIdentity = "RG"
            typeLine = "Instant"
            spell {
                val t = target(TargetFilter.Creature)
                effect = Effects.DealDamage(1, t)
            }
        })
        d.registerCard(card("Red Scenario Strike") {
            manaCost = "{R}"
            colorIdentity = "R"
            typeLine = "Instant"
            spell {
                val t = target(TargetFilter.Creature)
                effect = Effects.DealDamage(1, t)
            }
        })
        val p = d.player1
        val serpent = d.putCardInHand(p, "Stonecoil Serpent")
        d.giveColorlessMana(p, 3)
        assertNull(d.castXSpell(p, serpent, 3).error)
        d.resolveAll()
        val permanent = d.permanentsNamed(p, "Stonecoil Serpent").single()

        val multi = d.putCardInHand(p, "Two-Color Scenario Strike")
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)
        assertNotNull(d.castSpell(p, multi, listOf(permanent)).error)

        val mono = d.putCardInHand(p, "Red Scenario Strike")
        assertNull(d.castSpell(p, mono, listOf(permanent)).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(p, "Stonecoil Serpent").size)
    }
}
