package com.arcanatable.customcards

// Independently authored trusted-main checks. Never copied from the Copilot PR.
// Exercise Poxwalkers with real Argentum state transitions and both negative controls.
import com.arcanatable.customcards.cards.Poxwalkers
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class PoxwalkersIndependentCertificationTest {
    @Test
    fun graveyardZombieIgnoresAnOrdinaryHandCast() {
        val d = customScenario(Poxwalkers, "Grizzly Bears")
        val controller = d.player1
        val zombie = d.putCardInGraveyard(controller, "Poxwalkers")
        val bear = d.putCardInHand(controller, "Grizzly Bears")
        d.giveMana(controller, Color.GREEN)
        d.giveColorlessMana(controller, 1)
        assertNull(d.castSpell(controller, bear).error)
        d.resolveAll()
        assertTrue(d.getGraveyard(controller).contains(zombie))
        assertFalse(d.getPermanents(controller).contains(zombie))
    }

    @Test
    fun nonHandCastReturnsOnlyGraveyardZombieAndItIsTapped() {
        val d = customScenario(Poxwalkers, "Heralds of Tzeentch", "Grizzly Bears", "Plains")
        val p = d.player1
        val zombie = d.putCardInGraveyard(p, "Poxwalkers")
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Plains")
        val heralds = d.putCardInHand(p, "Heralds of Tzeentch")
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, heralds).error)
        d.resolveAll()
        assertFalse(d.getGraveyard(p).contains(zombie))
        assertTrue(d.getPermanents(p).contains(zombie))
        assertTrue(d.isTapped(zombie))
    }

    @Test
    fun anotherPlayersCastFromExileCannotReanimateMyZombie() {
        val d = customScenario(Poxwalkers, "Heralds of Tzeentch", "Grizzly Bears", "Plains")
        val zombie = d.putCardInGraveyard(d.player1, "Poxwalkers")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.putCardOnTopOfLibrary(d.player2, "Grizzly Bears")
        d.putCardOnTopOfLibrary(d.player2, "Plains")
        val heralds = d.putCardInHand(d.player2, "Heralds of Tzeentch")
        d.giveMana(d.player2, Color.BLUE)
        d.giveColorlessMana(d.player2, 4)
        assertNull(d.castSpell(d.player2, heralds).error)
        d.resolveAll()
        assertTrue(d.getGraveyard(d.player1).contains(zombie))
        assertFalse(d.getPermanents(d.player1).contains(zombie))
    }

    @Test
    fun graveyardTriggerDoesNotRetriggerZombieAlreadyOnBattlefield() {
        val d = customScenario(Poxwalkers, "Heralds of Tzeentch", "Grizzly Bears", "Plains")
        val p = d.player1
        val zombie = d.putCreatureOnBattlefield(p, "Poxwalkers")
        assertFalse(d.isTapped(zombie))
        d.putCardOnTopOfLibrary(p, "Grizzly Bears")
        d.putCardOnTopOfLibrary(p, "Plains")
        val heralds = d.putCardInHand(p, "Heralds of Tzeentch")
        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 4)
        assertNull(d.castSpell(p, heralds).error)
        d.resolveAll()
        assertTrue(d.getPermanents(p).contains(zombie))
        assertFalse(d.isTapped(zombie))
    }

    @Test
    fun lethalDeathtouchIsNotAnOrdinaryThreeDamageKill() {
        val d = customScenario(Poxwalkers, "Giant Spider")
        val zombie = d.putCreatureOnBattlefield(d.player1, "Poxwalkers")
        val giant = d.putCreatureOnBattlefield(d.player2, "Giant Spider")
        d.removeSummoningSickness(zombie)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(zombie), d.player2).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(d.player2, mapOf(giant to listOf(zombie))).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.resolveAll()
        assertFalse(d.getPermanents(d.player2).contains(giant))
        assertTrue(d.zoneNames(d.player2, Zone.GRAVEYARD).contains("Giant Spider"))
        assertTrue(d.zoneNames(d.player1, Zone.GRAVEYARD).contains("Poxwalkers"))
    }

    @Test
    fun zombieDeathtouchDoesNotGrantItToAnotherCreature() {
        val d = customScenario(Poxwalkers, "Grizzly Bears")
        val zombie = d.putCreatureOnBattlefield(d.player1, "Poxwalkers")
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        assertTrue(d.projected.hasKeyword(zombie, Keyword.DEATHTOUCH))
        assertFalse(d.projected.hasKeyword(bear, Keyword.DEATHTOUCH))
    }
}
