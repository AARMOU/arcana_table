package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Silence
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.*
import org.junit.Test

class SilenceScenarioTest {
    @Test
    fun opponentsCannotCastSpellsForTheRestOfTheTurn() {
        val d = customScenario(Silence, "Grizzly Bears")
        val caster = d.player1
        val opponent = d.player2
        val silence = d.putCardInHand(caster, "Silence")
        d.giveMana(caster, Color.WHITE)

        assertNull(d.castSpell(caster, silence).error)
        d.resolveAll()
        d.passPriority(caster)

        val bears = d.putCardInHand(opponent, "Grizzly Bears")
        d.giveMana(opponent, Color.GREEN, 2)
        assertNotNull(d.castSpell(opponent, bears).error)
    }
}
