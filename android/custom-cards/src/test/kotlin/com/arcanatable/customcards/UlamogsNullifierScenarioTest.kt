package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UlamogsNullifier
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class UlamogsNullifierScenarioTest {
    private fun setup() = customScenario(UlamogsNullifier, "Grizzly Bears", "Sol Ring", "Island")

    /** I cast Bears, then flash in Nullifier holding priority; its ETB targets the Bears spell. */
    private fun GameTestDriver.respondToOwnBears(c: Choices): EntityId {
        val bears = putCardInHand(player1, "Grizzly Bears")
        giveMana(player1, Color.GREEN); giveColorlessMana(player1, 1)
        assertNull(castSpell(player1, bears).error)
        val nullifier = putCardInHand(player1, "Ulamog's Nullifier")
        giveMana(player1, Color.BLUE); giveMana(player1, Color.BLACK); giveColorlessMana(player1, 2)
        assertNull(castSpell(player1, nullifier).error)
        resolveAll(c.copy(targets = listOf(bears)))
        return bears
    }

    @Test fun processingTwoOpponentCardsCountersTheSpell() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring"); d.putCardInExile(d.player2, "Grizzly Bears")
        d.respondToOwnBears(Choices(yes = true, pick = listOf("Sol Ring", "Grizzly Bears")))
        val nullifier = d.permanentsNamed(d.player1, "Ulamog's Nullifier").single()
        assertTrue(d.projected.hasKeyword(nullifier, Keyword.FLYING))
        assertTrue("Bears countered", d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
        assertTrue(d.getGraveyardCardNames(d.player1).contains("Grizzly Bears"))
        assertTrue(d.getExileCardNames(d.player2).isEmpty())
        assertEquals(listOf("Grizzly Bears", "Sol Ring"), d.getGraveyardCardNames(d.player2).sorted())
    }

    @Test fun decliningLetsTheSpellResolve() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring"); d.putCardInExile(d.player2, "Grizzly Bears")
        d.respondToOwnBears(Choices(yes = false))
        assertEquals(1, d.permanentsNamed(d.player1, "Grizzly Bears").size)
        assertEquals(2, d.getExileCardNames(d.player2).size)
    }

    @Test fun oneOpponentCardOrOwnCardsAreNotEnough() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player1, "Sol Ring"); d.putCardInExile(d.player1, "Grizzly Bears")
        d.respondToOwnBears(Choices(yes = true))
        assertEquals(1, d.permanentsNamed(d.player1, "Grizzly Bears").size)
        assertEquals(1, d.getExileCardNames(d.player2).size)
        assertEquals(2, d.getExileCardNames(d.player1).size)
    }

    @Test fun castWithNothingOnTheStackIsJustAFlyer() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring"); d.putCardInExile(d.player2, "Grizzly Bears")
        val card = d.putCardInHand(d.player1, "Ulamog's Nullifier")
        assertTrue(d.state.projectedState.getColors(card).isEmpty())
        d.giveMana(d.player1, Color.BLUE); d.giveMana(d.player1, Color.BLACK); d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, card).error)
        d.resolveAll(Choices(yes = true))
        assertEquals(1, d.permanentsNamed(d.player1, "Ulamog's Nullifier").size)
        assertEquals(2, d.getExileCardNames(d.player2).size)
    }
    @Test fun processingTwoOfThreeExiledCardsLeavesTheThirdExiled() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Grizzly Bears")
        d.putCardInExile(d.player2, "Island")

        d.respondToOwnBears(Choices(yes = true, pick = listOf("Sol Ring", "Grizzly Bears")))

        assertTrue("The spell must be countered after exactly two moves",
            d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
        assertEquals("Only the unselected card remains exiled",
            listOf("Island"), d.getExileCardNames(d.player2))
        assertEquals("The selected cards reach their owner's graveyard",
            listOf("Grizzly Bears", "Sol Ring"), d.getGraveyardCardNames(d.player2).sorted())
        assertFalse("Unselected exile must not move",
            d.getGraveyardCardNames(d.player2).contains("Island"))
    }

}
