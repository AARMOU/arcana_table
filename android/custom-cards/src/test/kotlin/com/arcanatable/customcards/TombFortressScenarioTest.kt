package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TombFortress
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Tomb Fortress (40K #168). Official ruling 2022-10-07: "The creature card that you return to the
 * battlefield doesn't have to be one of the cards you milled. It can be a card that was already in your
 * graveyard before you milled."
 */
class TombFortressScenarioTest {

    private fun game() = customScenario(TombFortress, "Grizzly Bears", "Hill Giant")

    private fun GameTestDriver.payForFortress(p: EntityId) {
        giveMana(p, Color.BLACK, 3)
        giveColorlessMana(p, 2)
    }

    @Test
    fun entersTappedAndTapsForBlack() {
        val d = game()
        val p = d.player1
        val fromHand = d.putCardInHand(p, "Tomb Fortress")
        assertNull(d.playLand(p, fromHand).error)
        d.resolveAll()
        assertTrue(d.isTapped(d.findPermanent(p, "Tomb Fortress")!!))

        val other = d.putPermanentOnBattlefield(p, "Tomb Fortress")
        assertFalse(d.isTapped(other))
        assertEquals(0, d.manaPool(p).black)
        assertNull(d.activate(p, other, 0).error)
        assertEquals(1, d.manaPool(p).black)
        assertTrue(d.isTapped(other))
    }

    @Test
    fun exilesItselfMillsFourAndReturnsAMilledCreatureUntapped() {
        val d = game()
        val p = d.player1
        val fortress = d.putPermanentOnBattlefield(p, "Tomb Fortress")
        val bears = d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        val librarySize = d.state.getZone(p, Zone.LIBRARY).size
        d.payForFortress(p)

        assertNull(d.activate(p, fortress, 1).error)
        assertTrue("exiled as a cost", fortress in d.state.getZone(p, Zone.EXILE))
        assertFalse(fortress in d.state.getBattlefield())
        d.resolveAll()

        assertEquals(librarySize - 4, d.state.getZone(p, Zone.LIBRARY).size)
        assertEquals(listOf("Plains", "Plains", "Plains"), d.zoneNames(p, Zone.GRAVEYARD))
        assertTrue(bears in d.state.getBattlefield())
        assertFalse("returns untapped", d.isTapped(bears))
        assertEquals(p, d.projected.getController(bears))
    }

    @Test
    fun mayReturnACardThatWasAlreadyThereInsteadOfAMilledOne() {
        val d = game()
        val p = d.player1
        val fortress = d.putPermanentOnBattlefield(p, "Tomb Fortress")
        val giant = d.putCardInGraveyard(p, "Hill Giant")
        val bears = d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.payForFortress(p)

        assertNull(d.activate(p, fortress, 1).error)
        d.resolveAll(Choices(pick = listOf("Hill Giant")))
        assertTrue(giant in d.state.getBattlefield())
        assertFalse(d.isTapped(giant))
        assertTrue("the milled creature stays in the graveyard", bears in d.state.getGraveyard(p))
    }

    @Test
    fun mayReturnAMilledCardOverOneAlreadyThere() {
        val d = game()
        val p = d.player1
        val fortress = d.putPermanentOnBattlefield(p, "Tomb Fortress")
        val giant = d.putCardInGraveyard(p, "Hill Giant")
        val bears = d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.payForFortress(p)

        assertNull(d.activate(p, fortress, 1).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))
        assertTrue(bears in d.state.getBattlefield())
        assertTrue(giant in d.state.getGraveyard(p))
    }

    @Test
    fun withNoCreatureCardInYourGraveyardItJustMills() {
        val d = game()
        val p = d.player1
        val fortress = d.putPermanentOnBattlefield(p, "Tomb Fortress")
        val opponentsBears = d.putCardInGraveyard(d.player2, "Grizzly Bears")
        val battlefieldBefore = d.state.getBattlefield().toSet() - fortress
        val librarySize = d.state.getZone(p, Zone.LIBRARY).size
        d.payForFortress(p)

        assertNull(d.activate(p, fortress, 1).error)
        d.resolveAll()
        assertEquals(librarySize - 4, d.state.getZone(p, Zone.LIBRARY).size)
        assertEquals(List(4) { "Plains" }, d.zoneNames(p, Zone.GRAVEYARD))
        assertEquals(battlefieldBefore, d.state.getBattlefield().toSet())
        assertTrue("an opponent's creature card is not eligible", opponentsBears in d.state.getGraveyard(d.player2))
        assertTrue(fortress in d.state.getZone(p, Zone.EXILE))
    }

    @Test
    fun activateOnlyAsASorcery() {
        val d = game()
        val p = d.player1
        val fortress = d.putPermanentOnBattlefield(p, "Tomb Fortress")
        d.putCardInGraveyard(p, "Hill Giant")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.passPriority(d.player2)
        assertEquals(p, d.state.priorityPlayerId)
        d.payForFortress(p)

        assertNotNull("opponent's turn", d.activate(p, fortress, 1).error)
        assertTrue(fortress in d.state.getBattlefield())
        assertFalse(d.isTapped(fortress))
    }

    @Test
    fun cannotActivateWithoutTheFullCost() {
        val d = game()
        val p = d.player1
        val fortress = d.putPermanentOnBattlefield(p, "Tomb Fortress")
        d.giveMana(p, Color.BLACK, 2)
        d.giveColorlessMana(p, 3)
        assertNotNull("only {B}{B}", d.activate(p, fortress, 1).error)
        assertTrue(fortress in d.state.getBattlefield())
    }
}
