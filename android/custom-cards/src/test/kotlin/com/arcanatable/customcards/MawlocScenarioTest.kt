package com.arcanatable.customcards

import com.arcanatable.customcards.cards.Mawloc
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class MawlocScenarioTest {
    @Test
    fun ravenousAtFiveAddsCountersAndDraws() {
        val d = customScenario(Mawloc)
        val p = d.player1
        val card = d.putCardInHand(p, "Mawloc")
        val handSize = d.getHand(p).size
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 5)
        assertNull(d.castXSpell(p, card, 5).error)
        d.resolveAll()
        assertEquals(7, d.projected.getPower(card))
        assertEquals(7, d.projected.getToughness(card))
        assertEquals(handSize, d.getHand(p).size)
    }

    @Test
    fun ravenousBelowFiveAddsCountersWithoutDrawing() {
        val d = customScenario(Mawloc)
        val p = d.player1
        val card = d.putCardInHand(p, "Mawloc")
        val handSize = d.getHand(p).size
        d.giveMana(p, Color.RED)
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 4)
        assertNull(d.castXSpell(p, card, 4).error)
        d.resolveAll()
        assertEquals(6, d.projected.getPower(card))
        assertEquals(6, d.projected.getToughness(card))
        assertEquals(handSize - 1, d.getHand(p).size)
    }

    @Test
    fun entryFightsOpponentCreatureAndExilesItWhenItDies() {
        val d = customScenario(Mawloc, "Grizzly Bears")
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val card = d.putCardInHand(d.player1, "Mawloc")
        d.giveMana(d.player1, Color.RED)
        d.giveMana(d.player1, Color.GREEN)
        assertNull(d.castXSpell(d.player1, card, 0).error)
        d.resolveAll(Choices(targets = listOf(target)))
        assertTrue(d.zoneNames(d.player2, Zone.EXILE).contains("Grizzly Bears"))
        assertFalse(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Grizzly Bears"))
        assertTrue(d.zoneNames(d.player1, Zone.GRAVEYARD).contains("Mawloc"))
    }

    @Test
    fun entryDoesNotTargetYourCreatureWhenOpponentControlsNoCreature() {
        val d = customScenario(Mawloc, "Grizzly Bears")
        val ownCreature = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val card = d.putCardInHand(d.player1, "Mawloc")
        d.giveMana(d.player1, Color.RED)
        d.giveMana(d.player1, Color.GREEN)
        assertNull(d.castXSpell(d.player1, card, 0).error)
        d.resolveAll()
        assertEquals(ownCreature, d.findPermanent(d.player1, "Grizzly Bears"))
        assertEquals(1, d.permanentsNamed(d.player1, "Mawloc").size)
    }

    @Test
    fun fightDoesNotExileCreatureThatSurvives() {
        val d = customScenario(Mawloc, "Colossal Dreadmaw")
        val target = d.putCreatureOnBattlefield(d.player2, "Colossal Dreadmaw")
        val card = d.putCardInHand(d.player1, "Mawloc")
        d.giveMana(d.player1, Color.RED)
        d.giveMana(d.player1, Color.GREEN)
        assertNull(d.castXSpell(d.player1, card, 0).error)
        d.resolveAll(Choices(targets = listOf(target)))
        assertEquals(target, d.findPermanent(d.player2, "Colossal Dreadmaw"))
        assertFalse(d.zoneNames(d.player2, Zone.EXILE).contains("Colossal Dreadmaw"))
    }
}
