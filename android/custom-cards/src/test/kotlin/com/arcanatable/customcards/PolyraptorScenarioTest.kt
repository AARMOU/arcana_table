package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Polyraptor
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PolyraptorScenarioTest {
    @Test
    fun combatDamageCreatesOneCopyToken() {
        val d = customScenario(Polyraptor, "Lightning Bolt")
        val raptor = d.putCreatureOnBattlefield(d.player2, "Polyraptor")
        val bolt = d.putCardInHand(d.player1, "Lightning Bolt")
        d.giveMana(d.player1, Color.RED)
        assertNull(d.castSpellWithTargets(d.player1, bolt, listOf(ChosenTarget.Permanent(raptor))).error)
        d.resolveAll()

        assertEquals(2, d.permanentsNamed(d.player2, "Polyraptor").size)
    }
}
