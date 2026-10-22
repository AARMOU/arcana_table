package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Reverberate
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import org.junit.Assert.*
import org.junit.Test

class ReverberateScenarioTest {
    @Test
    fun copiesAnInstantOnTheStackWithoutCastingTheCopy() {
        val d = customScenario(Reverberate)
        d.registerCard(card("Scenario Cantrip") {
            manaCost = "{U}"
            typeLine = "Instant"
            oracleText = "Draw a card."
            spell { effect = Effects.DrawCards(1) }
        })
        val p = d.player1
        val original = d.putCardInHand(p, "Scenario Cantrip")
        val reverberate = d.putCardInHand(p, "Reverberate")
        val startingCards = d.getHand(p).size
        d.giveMana(p, Color.BLUE)
        d.giveMana(p, Color.RED, 2)
        assertNull(d.castSpell(p, original).error)
        assertNull(d.castSpell(p, reverberate, listOf(original)).error)
        d.resolveAll()
        // Original + its copy each draw once; both cast cards leave hand.
        assertEquals(startingCards, d.getHand(p).size)
    }

    @Test
    fun cannotTargetAPermanentRatherThanASpellOnTheStack() {
        val d = customScenario(Reverberate, "Grizzly Bears")
        val p = d.player1
        val permanent = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val reverberate = d.putCardInHand(p, "Reverberate")
        d.giveMana(p, Color.RED, 2)
        assertNotNull(d.castSpell(p, reverberate, listOf(permanent)).error)
    }
}
