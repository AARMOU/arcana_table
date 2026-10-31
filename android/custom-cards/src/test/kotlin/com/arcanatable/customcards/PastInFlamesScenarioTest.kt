package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PastInFlames
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class PastInFlamesScenarioTest {
    @Test
    fun grantsManaCostFlashbackOnlyToYourGraveyardInstantsAndSorceries() {
        val d = customScenario(PastInFlames, "Divination", "Lightning Bolt", "Sol Ring")
        val p = d.player1
        val opponent = d.player2
        val divination = d.putCardInGraveyard(p, "Divination")
        val lightningBolt = d.putCardInGraveyard(p, "Lightning Bolt")
        val solRing = d.putCardInGraveyard(p, "Sol Ring")
        val opponentBolt = d.putCardInGraveyard(opponent, "Lightning Bolt")
        val pastInFlames = d.putCardInHand(p, "Past in Flames")

        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, pastInFlames).error)
        d.resolveAll()
        d.assertInGraveyard(p, "Past in Flames")

        d.giveMana(p, Color.BLUE)
        d.giveColorlessMana(p, 1)
        val underpaidDivination = d.legalActions(p).single { it.description == "Cast Divination (Flashback)" }
        assertEquals("{2}{U}", underpaidDivination.manaCostString)
        assertFalse(underpaidDivination.affordable)
        d.giveColorlessMana(p, 1)
        assertTrue(d.legalActions(p).single { it.description == "Cast Divination (Flashback)" }.affordable)
        assertNull(d.castSpell(p, divination).error)
        d.resolveAll()
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Divination"))

        d.giveMana(p, Color.RED)
        assertNull(d.castSpell(p, lightningBolt, listOf(opponent)).error)
        d.resolveAll()
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Lightning Bolt"))

        assertNotNull(d.castSpell(p, solRing).error)
        d.giveMana(opponent, Color.RED)
        d.passPriority(p)
        assertNotNull(d.castSpell(opponent, opponentBolt, listOf(p)).error)
    }

    @Test
    fun printedFlashbackCostsFourAndRedAndExilesPastInFlames() {
        val d = customScenario(PastInFlames)
        val p = d.player1
        val pastInFlames = d.putCardInGraveyard(p, "Past in Flames")

        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)
        val underpaidPastInFlames = d.legalActions(p).single {
            it.description == "Cast Past in Flames (Flashback)"
        }
        assertEquals("{4}{R}", underpaidPastInFlames.manaCostString)
        assertFalse(underpaidPastInFlames.affordable)
        d.giveColorlessMana(p, 1)
        assertTrue(d.legalActions(p).single {
            it.description == "Cast Past in Flames (Flashback)"
        }.affordable)
        assertNull(d.castSpell(p, pastInFlames).error)
        d.resolveAll()
        assertTrue(d.zoneNames(p, Zone.EXILE).contains("Past in Flames"))
    }

    @Test
    fun grantedFlashbackExpiresAtTheEndOfTheTurn() {
        val d = customScenario(PastInFlames, "Lightning Bolt")
        val p = d.player1
        val opponent = d.player2
        val lightningBolt = d.putCardInGraveyard(p, "Lightning Bolt")
        val pastInFlames = d.putCardInHand(p, "Past in Flames")

        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, pastInFlames).error)
        d.resolveAll()

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, opponent)
        d.passPriority(opponent)
        d.giveMana(p, Color.RED)
        assertNotNull(d.castSpell(p, lightningBolt, listOf(opponent)).error)
    }
}
