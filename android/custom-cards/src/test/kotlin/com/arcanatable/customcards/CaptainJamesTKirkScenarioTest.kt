package com.arcanatable.customcards

import com.arcanatable.customcards.cards.CaptainJamesTKirk
import com.arcanatable.customcards.support.customScenario
import com.arcanatable.customcards.support.permanentsNamed
import com.arcanatable.customcards.support.projected
import com.arcanatable.customcards.support.resolveStack
import com.arcanatable.customcards.support.zoneNames
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptainJamesTKirkScenarioTest {
    @Test
    fun emptyHandAllowsChoosingMultipleModes() {
        val d = customScenario(CaptainJamesTKirk)
        val player = d.player1
        d.state.getZone(ZoneKey(player, Zone.HAND)).toList().forEach(d::moveToGraveyard)
        val kirk = d.putCardInHand(player, "Captain James T. Kirk")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, kirk).error)
        while (d.pendingDecision == null && d.stackSize > 0) d.bothPass()

        var decision = d.state.pendingDecision as ChooseOptionDecision
        assertTrue(decision.options[1].contains("Create a 1/1 red Officer"))
        d.submitDecision(player, OptionChosenResponse(decision.id, 1))

        decision = d.state.pendingDecision as ChooseOptionDecision
        assertTrue(decision.options[1].contains("Creatures you control get +1/+0"))
        assertEquals("Don't choose a mode", decision.options.last())
        d.submitDecision(player, OptionChosenResponse(decision.id, 1))

        decision = d.state.pendingDecision as ChooseOptionDecision
        assertEquals("Don't choose a mode", decision.options.last())
        d.submitDecision(player, OptionChosenResponse(decision.id, decision.options.lastIndex))
        d.resolveStack()

        assertEquals(1, d.permanentsNamed(player, "Captain James T. Kirk").size)
        // Kirk is also an Officer, so exclude him when checking the 1/1 token.
        val officerToken = d.getCreatures(player).first {
            it != kirk && d.projected.hasSubtype(it, "Officer")
        }
        assertEquals(2, d.projected.getPower(officerToken))
        assertEquals(3, d.projected.getPower(kirk))
    }

    @Test
    fun cardsInHandRestrictsChoiceToOneMode() {
        val d = customScenario(CaptainJamesTKirk)
        val player = d.player1
        d.state.getZone(ZoneKey(player, Zone.HAND)).toList().forEach(d::moveToGraveyard)
        d.putCardInHand(player, "Mountain")
        val kirk = d.putCardInHand(player, "Captain James T. Kirk")
        d.giveColorlessMana(player, 2)
        d.giveMana(player, Color.RED)
        assertNull(d.castSpell(player, kirk).error)
        while (d.pendingDecision == null && d.stackSize > 0) d.bothPass()

        val decision = d.state.pendingDecision as ChooseOptionDecision
        assertEquals(4, decision.options.size)
        assertEquals("Don't choose a mode", decision.options.last())
        assertTrue(decision.options[0].contains("Discard a card"))
        d.submitDecision(player, OptionChosenResponse(decision.id, 0))
        assertTrue("choosing a mode with a card in hand ends mode selection", d.pendingDecision !is ChooseOptionDecision)
        d.resolveStack()

        assertTrue("the selected card was discarded", "Mountain" in d.zoneNames(player, Zone.GRAVEYARD))
    }
}
