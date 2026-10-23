package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Rewind
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RewindScenarioTest {
    @Test
    fun countersTargetSpellWhenNoLandsAreChosen() {
        val d = customScenario(Rewind, "Opt", "Counterspell")
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val counter = d.putCardInHand(d.player2, "Opt")
        d.giveMana(d.player2, Color.BLUE)
        assertNull(d.castSpell(d.player2, counter).error)
        d.passPriority(d.player2)

        val rewind = d.putCardInHand(d.player1, "Rewind")
        d.giveMana(d.player1, Color.BLUE)
        d.giveMana(d.player1, Color.BLUE)
        d.giveColorlessMana(d.player1, 2)
        val spellOnStack = d.getTopOfStack()!!
        assertNull(d.castSpellWithTargets(d.player1, rewind, listOf(ChosenTarget.Spell(spellOnStack))).error)
        d.resolveAll()

        assertEquals(listOf("Opt"), d.zoneNames(d.player2, Zone.GRAVEYARD))
    }
}
