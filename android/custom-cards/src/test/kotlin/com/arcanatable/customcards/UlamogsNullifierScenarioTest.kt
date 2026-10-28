package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UlamogsNullifier
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class UlamogsNullifierScenarioTest {
    private fun setup() = customScenario(UlamogsNullifier, "Grizzly Bears", "Sol Ring", "Island", "Rest in Peace", "Dark Ritual")

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


    @Test fun mixedOwnAndOpponentExilesProcessOnlyOpponentOwnedCards() {
        val d = setup()
        d.putCardInExile(d.player1, "Island")
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Grizzly Bears")

        d.respondToOwnBears(Choices(yes = true, pick = listOf("Sol Ring", "Grizzly Bears")))

        assertTrue("Processing two opponent-owned cards must counter the spell",
            d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
        assertEquals("The controller's own exiled card is ineligible and must stay exiled",
            listOf("Island"), d.getExileCardNames(d.player1))
        assertTrue("Both opponent-owned cards must leave exile",
            d.getExileCardNames(d.player2).isEmpty())
        assertEquals(listOf("Grizzly Bears", "Sol Ring"),
            d.getGraveyardCardNames(d.player2).sorted())
        assertFalse("Own exiled card cannot be processed",
            d.getGraveyardCardNames(d.player1).contains("Island"))
    }

    @Test fun decliningWithThreeEligibleCardsDoesNotMoveAnyOrCounter() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Grizzly Bears")
        d.putCardInExile(d.player2, "Island")

        d.respondToOwnBears(Choices(yes = false))

        assertEquals("Declining processing must leave the spell to resolve",
            1, d.permanentsNamed(d.player1, "Grizzly Bears").size)
        assertEquals(listOf("Grizzly Bears", "Island", "Sol Ring"),
            d.getExileCardNames(d.player2).sorted())
        assertTrue("Declining processing cannot move exiled cards",
            d.getGraveyardCardNames(d.player2).isEmpty())
    }

    /**
     * In a four-player pod, process two cards owned by different opponents.
     * A third opponent's unchosen exile and the controller's own exile must stay put.
     */
    @Test fun fourPlayerProcessingAcrossOpponentsKeepsUnchosenAndOwnExiles() {
        val d = setup()
        val seats = d.initMultiplayer(
            decks = List(4) { com.wingedsheep.sdk.model.Deck.of("Plains" to 40) },
        )
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN)
        d.putCardInExile(seats[0], "Island")
        d.putCardInExile(seats[1], "Sol Ring")
        d.putCardInExile(seats[2], "Grizzly Bears")
        d.putCardInExile(seats[3], "Island")

        d.respondToOwnBears(Choices(yes = true, pick = listOf("Sol Ring", "Grizzly Bears")))

        assertTrue("The targeted spell must be countered after both moves",
            d.permanentsNamed(seats[0], "Grizzly Bears").isEmpty())
        assertEquals(listOf("Island"), d.getExileCardNames(seats[0]))
        assertTrue(d.getExileCardNames(seats[1]).isEmpty())
        assertTrue(d.getExileCardNames(seats[2]).isEmpty())
        assertEquals(listOf("Island"), d.getExileCardNames(seats[3]))
        assertEquals(listOf("Sol Ring"), d.getGraveyardCardNames(seats[1]))
        assertEquals(listOf("Grizzly Bears"), d.getGraveyardCardNames(seats[2]))
        assertFalse("Own exile must not be processed",
            d.getGraveyardCardNames(seats[0]).contains("Island"))
        assertFalse("Unchosen opponent exile must not be processed",
            d.getGraveyardCardNames(seats[3]).contains("Island"))
    }


    /**
     * Prerequisite: prove that the shipped Argentum Rest in Peace definition
     * actually redirects a graveyard-bound card before relying on it as a fixture.
     */
    @Test fun restInPeaceFixtureExilesResolvedInstantInsteadOfGraveyard() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Rest in Peace")
        val ritual = d.putCardInHand(d.player1, "Dark Ritual")
        d.giveMana(d.player1, Color.BLACK)
        assertNull(d.castSpell(d.player1, ritual).error)
        d.resolveAll()

        assertTrue("Replacement fixture must exile a resolved instant",
            d.getExileCardNames(d.player1).contains("Dark Ritual"))
        assertFalse("Fixture is invalid if the instant reaches the graveyard",
            d.getGraveyardCardNames(d.player1).contains("Dark Ritual"))
    }

    /**
     * Oracle ruling: cards processed from exile can be redirected back to exile
     * by Rest in Peace; they are still processed, so Nullifier must counter.
     * This is a genuine replacement-effect engine scenario, not a mock.
     */
    @Test fun restInPeaceReplacementStillCountsTwoProcessedExiledCards() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Rest in Peace")
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")

        d.respondToOwnBears(Choices(yes = true, pick = listOf("Sol Ring", "Island")))

        assertTrue("Two processed cards must counter the targeted spell despite redirection",
            d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
        assertEquals("Both opponent-owned cards are redirected back into exile",
            listOf("Island", "Sol Ring"), d.getExileCardNames(d.player2).sorted())
        assertTrue("Neither processed card may enter the opponent's graveyard",
            d.getGraveyardCardNames(d.player2).isEmpty())
        // CR 400.7: replacement into the same exile zone is still a fresh
        // zone-change event, not a no-op on the old exiled objects.
        val processed = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.ownerId == d.player2 &&
                it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.EXILE }
            .map { it.entityName }.sorted()
        assertEquals("Both processed cards must leave and re-enter exile as new objects",
            listOf("Island", "Sol Ring"), processed)
        assertTrue("Rest in Peace also redirects the countered spell",
            d.getExileCardNames(d.player1).contains("Grizzly Bears"))
    }
}
