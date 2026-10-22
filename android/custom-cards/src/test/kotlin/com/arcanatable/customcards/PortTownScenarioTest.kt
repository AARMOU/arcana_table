package com.arcanatable.customcards

import com.arcanatable.customcards.cards.PortTown
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import org.junit.Assert.*
import org.junit.Test

class PortTownScenarioTest {
    @Test
    fun revealingPlainsEntersUntappedAndBothColorsAreAvailable() {
        val d = customScenario(PortTown)
        val p = d.player1
        val handBefore = d.getHand(p).size
        val port = d.putCardInHand(p, "Port Town")
        assertNull(d.playLand(p, port).error)
        d.resolveAll(Choices(pick = listOf("Plains")))
        val land = d.findPermanent(p, "Port Town")!!
        assertFalse(d.isTapped(land))
        assertEquals(handBefore, d.getHand(p).size)
        assertNull(d.activate(p, land, 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).white)
        d.untapPermanent(land)
        assertNull(d.activate(p, land, 1).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).blue)
    }

    @Test
    fun decliningRevealEntersTappedEvenWhenPlainsIsAvailable() {
        val d = customScenario(PortTown)
        val p = d.player1
        val port = d.putCardInHand(p, "Port Town")
        assertNull(d.playLand(p, port).error)
        d.resolveAll(Choices(yes = false))
        assertTrue(d.isTapped(d.findPermanent(p, "Port Town")!!))
    }

    @Test
    fun aHandWithNoPlainsOrIslandMustEnterTapped() {
        val d = CardPool.driver("Port Town").apply {
            initMirrorMatch(com.wingedsheep.sdk.model.Deck.of("Forest" to 40))
            passPriorityUntil(Step.PRECOMBAT_MAIN)
        }
        val p = d.player1
        val port = d.putCardInHand(p, "Port Town")
        assertNull(d.playLand(p, port).error)
        d.resolveAll()
        assertTrue(d.isTapped(d.findPermanent(p, "Port Town")!!))
    }
}
