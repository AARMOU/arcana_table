package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PastInFlames
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class PastInFlamesIndependentCertificationTest {
    @Test
    fun normalCastRequiresThreeGenericAndRedAndGrantsInstantAndSorceryFlashback() {
        val d = customScenario(PastInFlames, "Shock", "Divination")
        val p = d.player1
        val pastInFlames = d.putCardInHand(p, "Past in Flames")
        val shock = d.putCardInGraveyard(p, "Shock")
        val divination = d.putCardInGraveyard(p, "Divination")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 2)

        assertNotNull(d.castSpell(p, pastInFlames).error)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Past in Flames"))

        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, pastInFlames).error)
        d.resolveAll()
        assertTrue(d.getGraveyard(p).contains(pastInFlames))

        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, shock, listOf(d.player2)).error)
        d.resolveAll()
        assertEquals(18, d.getLifeTotal(d.player2))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Shock"))
        assertFalse(d.getGraveyard(p).contains(shock))

        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 1)
        assertNotNull("Divination's {2}{U} flashback cost must be paid in full", d.castSpell(p, divination).error)
        d.giveColorlessMana(p, 1)
        val handBeforeDivination = d.getHandSize(p)
        assertNull(d.castSpell(p, divination).error)
        d.resolveAll()
        assertEquals(handBeforeDivination + 2, d.getHandSize(p))
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Divination"))
        assertFalse(d.getGraveyard(p).contains(divination))
    }

    @Test
    fun pastInFlamesCanBeCastFromItsGraveyardForItsPrintedFlashbackCost() {
        val d = customScenario(PastInFlames)
        val p = d.player1
        val pastInFlames = d.putCardInGraveyard(p, "Past in Flames")
        d.giveColorlessMana(p, 4)

        assertNotNull(d.castSpell(p, pastInFlames).error)
        assertTrue(d.getGraveyard(p).contains(pastInFlames))

        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, pastInFlames).error)
        d.resolveAll()

        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Past in Flames"))
        assertFalse(d.getGraveyard(p).contains(pastInFlames))
    }

    @Test
    fun instantCastFromHandAfterResolutionDoesNotGainFlashback() {
        val d = customScenario(PastInFlames, "Shock")
        val p = d.player1
        val pastInFlames = d.putCardInHand(p, "Past in Flames")
        val shock = d.putCardInHand(p, "Shock")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, pastInFlames).error)
        d.resolveAll()

        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, shock, listOf(d.player2)).error)
        d.resolveAll()
        assertTrue(d.getGraveyard(p).contains(shock))

        d.giveMana(p, Color.RED)
        assertNotNull(d.castSpell(p, shock, listOf(d.player2)).error)
        assertTrue(d.getGraveyard(p).contains(shock))
        assertFalse(d.zoneNames(p, Zone.EXILE).contains("Shock"))
    }

    @Test
    fun creatureAndAnOpponentsInstantDoNotGainFlashback() {
        val d = customScenario(PastInFlames, "Grizzly Bears", "Shock")
        val p = d.player1
        val pastInFlames = d.putCardInHand(p, "Past in Flames")
        val creature = d.putCardInGraveyard(p, "Grizzly Bears")
        val opponentsShock = d.putCardInGraveyard(d.player2, "Shock")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, pastInFlames).error)
        d.resolveAll()

        assertNotNull(d.castSpell(p, creature).error)
        assertNotNull(d.castSpell(p, opponentsShock, listOf(d.player2)).error)
        assertTrue(d.getGraveyard(p).contains(creature))
        assertTrue(d.getGraveyard(d.player2).contains(opponentsShock))
    }

    @Test
    fun grantedFlashbackExpiresAtTheEndOfTheTurn() {
        val d = customScenario(PastInFlames, "Shock")
        val p = d.player1
        val shock = d.putCardInGraveyard(p, "Shock")
        val pastInFlames = d.putCardInHand(p, "Past in Flames")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, pastInFlames).error)
        d.resolveAll()

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertNull(d.passPriority(d.player2).error)
        d.giveMana(p, Color.RED)
        assertNotNull(d.castSpell(p, shock, listOf(d.player2)).error)
        assertTrue(d.getGraveyard(p).contains(shock))
        assertFalse(d.zoneNames(p, Zone.EXILE).contains("Shock"))
    }
}
