package com.arcanatable.customcards

import com.arcanatable.customcards.cards.BileBlight
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.card
import org.junit.Assert.*
import org.junit.Test

class BileBlightScenarioTest {
    @Test
    fun allSameNamedCreaturesOnBothSidesDieButOtherNamesSurvive() {
        val d = customScenario(BileBlight, "Grizzly Bears", "Llanowar Elves")
        val p = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val elf = d.putCreatureOnBattlefield(p, "Llanowar Elves")
        val spell = d.putCardInHand(p, "Bile Blight")
        d.giveMana(p, Color.BLACK, 2)
        assertNull(d.castSpell(p, spell, listOf(target)).error)
        d.resolveAll()
        assertTrue(d.permanentsNamed(p, "Grizzly Bears").isEmpty())
        assertTrue(d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        assertTrue(elf in d.getCreatures(p))
    }

    @Test
    fun secondaryHexproofCreatureIsNotTargetedAndReductionExpires() {
        val d = customScenario(BileBlight)
        val p = d.player1
        d.registerCard(card("Shielded Test Beast") {
            manaCost = "{4}"
            typeLine = "Creature — Beast"
            power = 4
            toughness = 4
            keywords(Keyword.HEXPROOF)
        })
        val own = d.putCreatureOnBattlefield(p, "Shielded Test Beast")
        val enemy = d.putCreatureOnBattlefield(d.player2, "Shielded Test Beast")
        val spell = d.putCardInHand(p, "Bile Blight")
        d.giveMana(p, Color.BLACK, 2)
        assertNull(d.castSpell(p, spell, listOf(own)).error)
        d.resolveAll()
        assertEquals(1, d.projected.getPower(own))
        assertEquals(1, d.projected.getToughness(enemy))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertEquals(4, d.projected.getPower(own))
        assertEquals(4, d.projected.getToughness(enemy))
    }
}
