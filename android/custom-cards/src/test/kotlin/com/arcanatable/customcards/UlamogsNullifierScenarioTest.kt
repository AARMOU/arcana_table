package com.arcanatable.customcards

import com.arcanatable.customcards.cards.UlamogsNullifier
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class UlamogsNullifierScenarioTest {
    private fun setup() = customScenario(UlamogsNullifier, "Grizzly Bears", "Sol Ring", "Island", "Rest in Peace", "Dark Ritual", "Carnage Tyrant", "Counterspell")

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

    /**
     * Flash timing and ownership must also work when the targeted spell belongs
     * to an opponent, not only when responding to our own spell.
     */
    private fun GameTestDriver.respondToOpponentsSpell(c: Choices, spellName: String = "Grizzly Bears") {
        passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN, player2)
        val spell = putCardInHand(player2, spellName)
        when (spellName) {
            "Sol Ring" -> giveColorlessMana(player2, 1)
            "Dark Ritual" -> giveMana(player2, Color.BLACK)
            "Carnage Tyrant" -> { giveColorlessMana(player2, 4); giveMana(player2, Color.GREEN); giveMana(player2, Color.GREEN) }
            else -> { giveMana(player2, Color.GREEN); giveColorlessMana(player2, 1) }
        }
        assertNull(castSpell(player2, spell).error)
        assertEquals("Caster retains priority after casting", player2, priorityPlayer)
        assertNull(passPriority(player2).error)
        assertEquals("Nullifier controller must receive priority", player1, priorityPlayer)
        val nullifier = putCardInHand(player1, "Ulamog's Nullifier")
        giveMana(player1, Color.BLUE); giveMana(player1, Color.BLACK); giveColorlessMana(player1, 2)
        assertNull(castSpell(player1, nullifier).error)
        resolveAll(c.copy(targets = listOf(spell)))
    }

    /**
     * Processing two cards is independent of whether the target spell can be
     * countered. Carnage Tyrant's Oracle text forbids countering the spell;
     * its hexproof applies only on the battlefield, not on the stack.
     */
    @Test fun uncounterableOpponentsCreatureStillResolvesAfterTwoCardsAreProcessed() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")
        d.respondToOpponentsSpell(
            Choices(yes = true, pick = listOf("Sol Ring", "Island")),
            spellName = "Carnage Tyrant",
        )
        assertEquals("Uncounterable creature spell must resolve despite Nullifier",
            1, d.permanentsNamed(d.player2, "Carnage Tyrant").size)
        assertEquals("Nullifier still processes exactly two opponent-owned cards",
            listOf("Island", "Sol Ring"), d.getGraveyardCardNames(d.player2).sorted())
        assertTrue(d.getExileCardNames(d.player2).isEmpty())
    }

    /**
     * A replacement effect cannot override a spell's uncounterability:
     * processing still moves two exiled cards as new exile objects, while
     * Carnage Tyrant must resolve rather than become a countered spell.
     */
    @Test fun uncounterableCreatureWithRestInPeaceStillResolvesAfterProcessing() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Rest in Peace")
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")

        d.respondToOpponentsSpell(
            Choices(yes = true, pick = listOf("Sol Ring", "Island")),
            spellName = "Carnage Tyrant",
        )

        assertEquals("Uncounterable creature must resolve even when processing succeeds",
            1, d.permanentsNamed(d.player2, "Carnage Tyrant").size)
        assertEquals("Rest in Peace must preserve both processed cards in exile",
            listOf("Island", "Sol Ring"), d.getExileCardNames(d.player2).sorted())
        assertTrue("Neither processed card may enter the graveyard",
            d.getGraveyardCardNames(d.player2).isEmpty())
        val processed = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.ownerId == d.player2 &&
                it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.EXILE }
            .map { it.entityName }.sorted()
        assertEquals("Both cards must be processed as new exile objects",
            listOf("Island", "Sol Ring"), processed)
    }

    /**
     * A genuine Counterspell action removes the already targeted spell while
     * Nullifier's ETB trigger is waiting on the stack. On resolution its sole
     * target is illegal, so the entire ability must fail to resolve: it may
     * neither offer processing nor move either opponent-owned exile object.
     * No test-state zone mutation is used to remove the targeted spell.
     */
    @Test fun targetSpellCounteredBeforeNullifierTriggerResolvesDoesNotProcessExiles() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")

        val bears = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.GREEN)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, bears).error)
        val nullifier = d.putCardInHand(d.player1, "Ulamog's Nullifier")
        d.giveMana(d.player1, Color.BLUE)
        d.giveMana(d.player1, Color.BLACK)
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, nullifier).error)

        // Resolve only the creature; select the Bears spell as its ETB target.
        assertNull(d.bothPass().error)
        assertTrue("Nullifier ETB must request a spell target",
            d.state.pendingDecision is com.wingedsheep.engine.core.ChooseTargetsDecision)
        d.decide(Choices(targets = listOf(bears)))
        assertNull("Target choice must be complete", d.state.pendingDecision)
        d.assertStackSize(2)

        // Counterspell, not a state fixture, removes Bears from the stack first.
        val counterspell = d.putCardInHand(d.player1, "Counterspell")
        d.giveMana(d.player1, Color.BLUE, 2)
        assertNull(d.castSpellWithTargets(
            d.player1, counterspell,
            listOf(com.wingedsheep.engine.state.components.stack.ChosenTarget.Spell(bears)),
        ).error)
        d.resolveAll(Choices(yes = true, pick = listOf("Sol Ring", "Island")))

        assertEquals(1, d.permanentsNamed(d.player1, "Ulamog's Nullifier").size)
        assertTrue("The target spell was countered by Counterspell",
            d.getGraveyardCardNames(d.player1).contains("Grizzly Bears"))
        assertEquals("Illegal ETB target must leave opponent exiles untouched",
            listOf("Island", "Sol Ring"), d.getExileCardNames(d.player2).sorted())
        assertTrue("An illegal target cannot process either exiled card",
            d.getGraveyardCardNames(d.player2).isEmpty())
        val processed = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.ownerId == d.player2 &&
                it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE }
        assertTrue("No exile processing may occur when the ETB target is illegal",
            processed.isEmpty())
    }

    @Test fun opponentsSpellCanBeCounteredAfterProcessingTheirExiledCards() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")
        d.respondToOpponentsSpell(Choices(yes = true, pick = listOf("Sol Ring", "Island")))
        assertEquals(1, d.permanentsNamed(d.player1, "Ulamog's Nullifier").size)
        assertTrue("Opponent's spell was countered", d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        assertEquals(listOf("Grizzly Bears", "Island", "Sol Ring"),
            d.getGraveyardCardNames(d.player2).sorted())
        assertTrue(d.getExileCardNames(d.player2).isEmpty())
    }

    @Test fun decliningProcessingOfOpponentsSpellLetsItResolve() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")
        d.respondToOpponentsSpell(Choices(yes = false))
        assertEquals(1, d.permanentsNamed(d.player2, "Grizzly Bears").size)
        assertEquals(listOf("Island", "Sol Ring"), d.getExileCardNames(d.player2).sorted())
        assertTrue(d.getGraveyardCardNames(d.player2).isEmpty())
    }

    /** Nullifier can target any spell, not just a creature spell. */
    @Test fun opponentsArtifactSpellIsCounteredAfterProcessingTwoExiles() {
        val d = setup()
        d.putCardInExile(d.player2, "Island")
        d.putCardInExile(d.player2, "Grizzly Bears")
        d.respondToOpponentsSpell(
            Choices(yes = true, pick = listOf("Island", "Grizzly Bears")),
            spellName = "Sol Ring",
        )
        assertTrue("The opponent's artifact spell must be countered",
            d.permanentsNamed(d.player2, "Sol Ring").isEmpty())
        assertEquals(listOf("Grizzly Bears", "Island", "Sol Ring"),
            d.getGraveyardCardNames(d.player2).sorted())
        assertTrue("Exactly two opponent-owned exiles were processed",
            d.getExileCardNames(d.player2).isEmpty())
    }

    /** Declining processing cannot counter an artifact spell either. */
    @Test fun decliningProcessingOfOpponentsArtifactSpellLetsItResolve() {
        val d = setup()
        d.putCardInExile(d.player2, "Island")
        d.putCardInExile(d.player2, "Grizzly Bears")
        d.respondToOpponentsSpell(Choices(yes = false), spellName = "Sol Ring")
        assertEquals(1, d.permanentsNamed(d.player2, "Sol Ring").size)
        assertEquals(listOf("Grizzly Bears", "Island"),
            d.getExileCardNames(d.player2).sorted())
        assertTrue(d.getGraveyardCardNames(d.player2).isEmpty())
    }

    /** The processor ETB may counter an opponent's instant, not only permanent spells. */
    @Test fun opponentsInstantSpellIsCounteredAfterProcessingTwoExiles() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")
        d.respondToOpponentsSpell(
            Choices(yes = true, pick = listOf("Sol Ring", "Island")),
            spellName = "Dark Ritual",
        )
        assertEquals("The instant and both processed cards go to their owner's graveyard",
            listOf("Dark Ritual", "Island", "Sol Ring"), d.getGraveyardCardNames(d.player2).sorted())
        assertTrue("Both processed exiles must leave exile", d.getExileCardNames(d.player2).isEmpty())
    }

    /** Declining the optional processing must let the opponent's instant resolve. */
    @Test fun decliningProcessingOfOpponentsInstantSpellLetsItResolve() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")
        d.respondToOpponentsSpell(Choices(yes = false), spellName = "Dark Ritual")
        assertEquals("The resolved instant goes to its owner's graveyard",
            listOf("Dark Ritual"), d.getGraveyardCardNames(d.player2))
        assertEquals("Declining must preserve both exiled cards",
            listOf("Island", "Sol Ring"), d.getExileCardNames(d.player2).sorted())
    }

    /**
     * Cross-check two independent Oracle constraints together: the targeted
     * spell belongs to an opponent, and Rest in Peace replaces both the
     * processing moves and the countered spell's graveyard destination.
     */
    @Test fun restInPeaceOpponentSpellIsCounteredAfterProcessingTwoExiles() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Rest in Peace")
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")

        d.respondToOpponentsSpell(Choices(yes = true, pick = listOf("Sol Ring", "Island")))

        assertTrue("Opponent's targeted spell must be countered",
            d.permanentsNamed(d.player2, "Grizzly Bears").isEmpty())
        assertEquals("Rest in Peace must also exile the opponent's countered spell",
            listOf("Grizzly Bears", "Island", "Sol Ring"), d.getExileCardNames(d.player2).sorted())
        assertTrue("Neither processed cards nor countered spell enter the graveyard",
            d.getGraveyardCardNames(d.player2).isEmpty())
        val reexiled = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.ownerId == d.player2 &&
                it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.EXILE }
            .map { it.entityName }.sorted()
        assertEquals("Both selected cards must become new exile objects",
            listOf("Island", "Sol Ring"), reexiled)
    }

    /** Declining the optional processing must still allow an opponent's spell to resolve. */
    @Test fun restInPeaceDeclineOnOpponentSpellPreservesExilesAndLetsItResolve() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Rest in Peace")
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")

        d.respondToOpponentsSpell(Choices(yes = false))

        assertEquals("Declining cannot counter the opponent's spell",
            1, d.permanentsNamed(d.player2, "Grizzly Bears").size)
        assertEquals(listOf("Island", "Sol Ring"), d.getExileCardNames(d.player2).sorted())
        assertTrue(d.getGraveyardCardNames(d.player2).isEmpty())
        val reexiled = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.ownerId == d.player2 &&
                it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.EXILE }
        assertTrue("Declining must not process either exiled object", reexiled.isEmpty())
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
    /**
     * Negative replacement fixture: declining the optional processor cost must
     * neither counter the targeted spell nor generate EXILE-to-EXILE moves.
     */
    @Test fun restInPeaceDeclineDoesNotProcessCardsOrCounter() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Rest in Peace")
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")

        d.respondToOwnBears(Choices(yes = false))

        assertEquals("Declining the optional processing lets the targeted spell resolve",
            1, d.permanentsNamed(d.player1, "Grizzly Bears").size)
        assertEquals(listOf("Island", "Sol Ring"), d.getExileCardNames(d.player2).sorted())
        assertTrue("Declining must not move either exiled card",
            d.getGraveyardCardNames(d.player2).isEmpty())
        val reexiled = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.ownerId == d.player2 &&
                it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.EXILE }
        assertTrue("Declining cannot produce a replacement zone-change event", reexiled.isEmpty())
    }

    /**
     * Replacement effects must not turn our own exiled cards into eligible
     * processing material. One opponent-owned exile is still insufficient.
     */
    @Test fun restInPeaceDoesNotAllowProcessingOwnExiledCards() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Rest in Peace")
        d.putCardInExile(d.player1, "Sol Ring")
        d.putCardInExile(d.player1, "Island")
        d.putCardInExile(d.player2, "Grizzly Bears")

        d.respondToOwnBears(Choices(yes = true))

        assertEquals("A single opponent-owned exile cannot pay the two-card processing cost",
            1, d.permanentsNamed(d.player1, "Grizzly Bears").size)
        assertEquals(listOf("Island", "Sol Ring"), d.getExileCardNames(d.player1).sorted())
        assertEquals(listOf("Grizzly Bears"), d.getExileCardNames(d.player2))
        val reexiled = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.EXILE }
        assertTrue("Ineligible exile must not be processed under Rest in Peace", reexiled.isEmpty())
    }

    /**
     * In multiplayer, a replacement effect must not collapse separately owned
     * process selections into one shared exile transaction. Each selected card
     * changes exile objects under Rest in Peace; unselected/own cards do not.
     */
    @Test fun fourPlayerRestInPeaceProcessesTwoDifferentOpponentsAsNewExileObjects() {
        val d = setup()
        val seats = d.initMultiplayer(
            decks = List(4) { com.wingedsheep.sdk.model.Deck.of("Plains" to 40) },
        )
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN)
        d.putPermanentOnBattlefield(seats[0], "Rest in Peace")
        d.putCardInExile(seats[0], "Island")
        d.putCardInExile(seats[1], "Sol Ring")
        d.putCardInExile(seats[2], "Grizzly Bears")
        d.putCardInExile(seats[3], "Island")

        d.respondToOwnBears(Choices(yes = true, pick = listOf("Sol Ring", "Grizzly Bears")))

        assertTrue("Both selected opponent-owned cards must pay the processor cost",
            d.permanentsNamed(seats[0], "Grizzly Bears").isEmpty())
        assertTrue("Rest in Peace must exile the countered target",
            d.getExileCardNames(seats[0]).contains("Grizzly Bears"))
        assertEquals(listOf("Sol Ring"), d.getExileCardNames(seats[1]))
        assertEquals(listOf("Grizzly Bears"), d.getExileCardNames(seats[2]))
        assertEquals(listOf("Island"), d.getExileCardNames(seats[3]))
        assertTrue(d.getGraveyardCardNames(seats[1]).isEmpty())
        assertTrue(d.getGraveyardCardNames(seats[2]).isEmpty())
        val transitions = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.EXILE }
            .map { it.ownerId to it.entityName }
        assertEquals("Each selected opponent must have exactly one replacement transition",
            listOf(seats[1] to "Sol Ring", seats[2] to "Grizzly Bears").toSet(),
            transitions.toSet())
        assertEquals("No duplicate processing transitions", 2, transitions.size)
    }

    /**
     * Under Rest in Peace, choosing two of three cards owned by one opponent
     * must process only the chosen objects. The third remains the original
     * exiled object; the two chosen cards produce exactly two EXILE-to-EXILE
     * zone changes despite all three card names still appearing in exile.
     */
    @Test fun restInPeaceProcessesExactlyTwoOfThreeAndLeavesThirdUnchanged() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Rest in Peace")
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Grizzly Bears")
        d.putCardInExile(d.player2, "Island")

        d.respondToOwnBears(Choices(yes = true, pick = listOf("Sol Ring", "Grizzly Bears")))

        assertTrue("Processing two cards must counter the targeted spell",
            d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
        assertEquals("All three names remain exiled under the replacement effect",
            listOf("Grizzly Bears", "Island", "Sol Ring"), d.getExileCardNames(d.player2).sorted())
        assertTrue("Replacement effect prevents opponent graveyard moves",
            d.getGraveyardCardNames(d.player2).isEmpty())
        val reexiled = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.ownerId == d.player2 &&
                it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.EXILE }
            .map { it.entityName }
        assertEquals("Exactly the two selected cards must become new exile objects",
            listOf("Grizzly Bears", "Sol Ring"), reexiled.sorted())
        assertFalse("The unselected Island must not be processed",
            reexiled.contains("Island"))
    }


    /**
     * Two physically distinct opponent-owned cards may share the same printed name.
     * Rest in Peace must process both as separate exile objects, while an identically
     * named card owned by Nullifier's controller stays in exile.
     */
    @Test fun restInPeaceProcessesTwoSameNamedOpponentExilesWithoutTouchingOwnCopy() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Rest in Peace")
        d.putCardInExile(d.player1, "Sol Ring")
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Sol Ring")

        d.respondToOwnBears(Choices(yes = true, pick = listOf("Sol Ring")))

        assertTrue("Processing two distinct cards with the same name must counter",
            d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
        assertEquals("Own Sol Ring stays exiled; Rest in Peace also exiles the countered Bears spell",
            listOf("Grizzly Bears", "Sol Ring"), d.getExileCardNames(d.player1).sorted())
        assertEquals(listOf("Sol Ring", "Sol Ring"), d.getExileCardNames(d.player2).sorted())
        assertTrue(d.getGraveyardCardNames(d.player2).isEmpty())
        val processed = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.entityName == "Sol Ring" }
        assertEquals("Exactly two separate opponent-owned exile objects must move", 2, processed.size)
        assertTrue("Own same-named exile must never be selected",
            processed.all { it.ownerId == d.player2 })
    }

    /** A same-named own exile cannot be counted as the second opponent-owned card. */
    @Test fun restInPeaceCannotCountOwnSameNamedExileTowardTwoCardCost() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Rest in Peace")
        d.putCardInExile(d.player1, "Sol Ring")
        d.putCardInExile(d.player2, "Sol Ring")

        d.respondToOwnBears(Choices(yes = true, pick = listOf("Sol Ring")))

        assertEquals("One eligible opponent-owned card is insufficient to counter",
            1, d.permanentsNamed(d.player1, "Grizzly Bears").size)
        assertEquals(listOf("Sol Ring"), d.getExileCardNames(d.player1))
        assertEquals(listOf("Sol Ring"), d.getExileCardNames(d.player2))
        val processed = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.EXILE }
        assertTrue("No card may be processed when the two-card cost is unavailable",
            processed.isEmpty())
    }

    /** Without replacement, two same-named exiled cards are two actual processing moves. */
    @Test fun duplicateNamedOpponentExilesReachGraveyardAsTwoCards() {
        val d = setup()
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Sol Ring")

        d.respondToOwnBears(Choices(yes = true, pick = listOf("Sol Ring")))

        assertTrue("Processing two separate cards must counter the targeted spell",
            d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
        assertTrue("Both same-named opponent cards must leave exile",
            d.getExileCardNames(d.player2).isEmpty())
        assertEquals("Both cards must reach their owner's graveyard",
            listOf("Sol Ring", "Sol Ring"), d.getGraveyardCardNames(d.player2).sorted())
        val processed = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.ownerId == d.player2 &&
                it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.GRAVEYARD &&
                it.entityName == "Sol Ring" }
        assertEquals("Two cards with the same name must generate two real zone changes",
            2, processed.size)
    }

    /**
     * The target may be an opponent's instant while Rest in Peace redirects
     * both processed cards and the countered instant back into exile.
     * This must still count as paying Nullifier's optional two-card cost.
     */
    @Test fun restInPeaceCountersOpponentsInstantAfterTwoRealReplacementMoves() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Rest in Peace")
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")

        d.respondToOpponentsSpell(
            Choices(yes = true, pick = listOf("Sol Ring", "Island")),
            spellName = "Dark Ritual",
        )

        assertEquals("Rest in Peace redirects the processed cards and countered instant",
            listOf("Dark Ritual", "Island", "Sol Ring"), d.getExileCardNames(d.player2).sorted())
        assertTrue("No opponent-owned card may reach the graveyard",
            d.getGraveyardCardNames(d.player2).isEmpty())
        val processed = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.ownerId == d.player2 &&
                it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.EXILE }
            .map { it.entityName }.sorted()
        assertEquals("Two separately owned exile objects must be processed",
            listOf("Island", "Sol Ring"), processed)
    }

    /**
     * Declining Nullifier's optional cost must not create EXILE-to-EXILE
     * replacement moves, even though Rest in Peace exiles a resolved instant.
     */
    @Test fun restInPeaceDeclineOnOpponentsInstantNeverProcessesExiledCards() {
        val d = setup()
        d.putPermanentOnBattlefield(d.player1, "Rest in Peace")
        d.putCardInExile(d.player2, "Sol Ring")
        d.putCardInExile(d.player2, "Island")

        d.respondToOpponentsSpell(Choices(yes = false), spellName = "Dark Ritual")

        assertEquals("Resolved instant is exiled; original two exiles remain",
            listOf("Dark Ritual", "Island", "Sol Ring"), d.getExileCardNames(d.player2).sorted())
        assertTrue(d.getGraveyardCardNames(d.player2).isEmpty())
        val processed = d.events.filterIsInstance<com.wingedsheep.engine.core.ZoneChangeEvent>()
            .filter { it.ownerId == d.player2 &&
                it.fromZone == com.wingedsheep.sdk.core.Zone.EXILE &&
                it.toZone == com.wingedsheep.sdk.core.Zone.EXILE }
        assertTrue("Declining may not move either exiled object", processed.isEmpty())
    }


    /**
     * A face-down card in exile has no baseline identity access even for its
     * owner (CR 708.5). Exercise Nullifier's REAL pending selection and the
     * pinned engine's player-facing DecisionMasker, not a fabricated decision.
     * The test driver selects by privileged internal name; that is not UI data.
     */
    @Test fun faceDownOpponentExileIsMaskedInActualProcessorChoiceAndStillProcesses() {
        val d = setup()
        val hidden = d.putCardInExile(d.player2, "Sol Ring")
        d.addComponent(hidden, com.wingedsheep.engine.state.components.identity.FaceDownComponent)
        d.putCardInExile(d.player2, "Island")
        // With exactly two eligible exiles, Argentum selects both automatically.
        // A third eligible card is required to exercise a real choice/DecisionMasker.
        d.putCardInExile(d.player2, "Grizzly Bears")

        val bears = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.GREEN)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, bears).error)
        val nullifier = d.putCardInHand(d.player1, "Ulamog's Nullifier")
        d.giveMana(d.player1, Color.BLUE)
        d.giveMana(d.player1, Color.BLACK)
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, nullifier).error)

        assertNull(d.bothPass().error)
        assertTrue(d.state.pendingDecision is com.wingedsheep.engine.core.ChooseTargetsDecision)
        d.decide(Choices(targets = listOf(bears)))
        assertNull(d.bothPass().error)
        assertTrue("Processing is optional", d.state.pendingDecision is com.wingedsheep.engine.core.YesNoDecision)
        d.decide(Choices(yes = true))
        // Some continuations resume on the next stack priority cycle.
        repeat(3) {
            if (d.state.pendingDecision == null && d.state.stack.isNotEmpty()) {
                assertNull(d.bothPass().error)
            }
        }
        val choice = d.state.pendingDecision as? com.wingedsheep.engine.core.SelectCardsDecision
            ?: throw AssertionError(
                "Nullifier must offer a real two-card selection; pending=" +
                    d.state.pendingDecision + " stack=" + d.getStackSpellNames() +
                    " opponentExile=" + d.getExileCardNames(d.player2)
            )
        assertTrue("The hidden opponent-owned card must be eligible", hidden in choice.options)

        val visibility = com.wingedsheep.engine.view.Visibility(
            d.cardRegistry, conditionEvaluator = d.services.conditionEvaluator,
        )
        val masker = com.wingedsheep.engine.view.DecisionMasker(visibility)
        for (viewer in listOf(d.player1, d.player2)) {
            assertTrue("Face-down exile is hidden even from its owner",
                visibility.isCardIdentityHiddenFrom(d.state, hidden, viewer))
            val masked = masker.maskFor(choice, d.state, viewer)
                as com.wingedsheep.engine.core.SelectCardsDecision
            val info = masked.cardInfo?.get(hidden)
                ?: throw AssertionError("Selection must have masked card metadata")
            assertNotEquals("Selection must not reveal a face-down card's name",
                "Sol Ring", info.name)
            assertEquals("Selection must not reveal hidden mana cost", "", info.manaCost)
        }

        // The driver uses engine-internal names only to submit selected entity IDs.
        d.decide(Choices(pick = listOf("Sol Ring", "Island")))
        d.resolveAll()
        assertTrue("Processing two opponent-owned cards must counter Bears",
            d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
        assertEquals("Unchosen third exile must remain", listOf("Grizzly Bears"),
            d.getExileCardNames(d.player2))
        assertEquals(listOf("Island", "Sol Ring"), d.getGraveyardCardNames(d.player2).sorted())
    }


    /**
     * A nonselected face-down exile must stay in exile and remain hidden after
     * the two OTHER opponent-owned cards are processed. This covers the
     * negative privacy/zone branch of the real SelectCardsDecision.
     */
    /**
     * Isolates the client-snapshot face-down exile visibility contract from
     * Nullifier, stack resolution, and pending decisions. Both player views and
     * a spectator must be masked; an ordinary face-up exile stays public.
     *
     * This is a fail-closed regression for the shared engine projection. Do not
     * "fix" it by changing Nullifier's choice metadata or hiding Android UI.
     */
    @Test fun faceDownExileSnapshotIsMaskedWithoutNullifier() {
        val d = setup()
        val hidden = d.putCardInExile(d.player2, "Sol Ring")
        d.addComponent(hidden, com.wingedsheep.engine.state.components.identity.FaceDownComponent)
        val faceUp = d.putCardInExile(d.player2, "Island")
        val visibility = com.wingedsheep.engine.view.Visibility(
            d.cardRegistry, conditionEvaluator = d.services.conditionEvaluator,
        )
        val transformer = com.wingedsheep.engine.view.ClientStateTransformer(
            d.cardRegistry, predicateEvaluator = d.services.predicateEvaluator,
        )

        // Collect failures from EVERY perspective before failing. The original
        // fail-closed checks remain mandatory; a first-viewer leak must not hide
        // whether the owner, spectator, or face-up control also misprojects.
        val violations = mutableListOf<String>()
        for (viewer in listOf(d.player1, d.player2)) {
            val snapshot = transformer.transform(d.state, viewer)
            val card = snapshot.cards[hidden]
            val visibleControl = snapshot.cards[faceUp]
            val perspective = if (viewer == d.player1) "non-owner" else "owner"
            val context = "viewer=$perspective id=$viewer hiddenName=${card?.name}" +
                " faceDown=${card?.isFaceDown} owner=${card?.ownerId}" +
                " controller=${card?.controllerId} faceUp=${visibleControl?.name}"
            try {
                assertTrue("Engine visibility must conceal face-down exile: $context",
                    visibility.isCardIdentityHiddenFrom(d.state, hidden, viewer))
                assertEquals("Face-up exile must remain public: $context",
                    "Island", visibleControl?.name)
                assertFalse("Face-up control must not be face-down: $context",
                    visibleControl?.isFaceDown == true)
                assertNotNull("Face-down exile must have a masked client card: $context", card)
                assertTrue("Client must mark hidden exile face-down: $context", card!!.isFaceDown)
                assertNotEquals("Snapshot leaked Sol Ring: $context", "Sol Ring", card.name)
                assertEquals("Mana cost leaked: $context", "", card.manaCost)
                assertEquals("Type line leaked: $context", "", card.typeLine)
                assertEquals("Rules text leaked: $context", "", card.oracleText)
                assertNotEquals("Revealed identity leaked: $context", "Sol Ring", card.revealedName)
                assertNull("Revealed art URI leaked: $context", card.revealedImageUri)
                assertTrue("Printed card types leaked: $context", card.cardTypes.isEmpty())
                assertTrue("Printed colors leaked: $context", card.colors.isEmpty())
                assertTrue("Printed keywords leaked: $context", card.keywords.isEmpty())
                assertNull("Printed power leaked: $context", card.power)
                assertNull("Printed toughness leaked: $context", card.toughness)
            } catch (failure: AssertionError) {
                violations.add(failure.message ?: "Projection assertion failed: $context")
            }
        }
        val spectator = transformer.transform(d.state, d.player1, isSpectator = true)
        val spectatorHidden = spectator.cards[hidden]
        val spectatorControl = spectator.cards[faceUp]
        try {
            assertNotNull("Face-down exile must appear as masked spectator card", spectatorHidden)
            assertTrue("Spectator must see face-down flag", spectatorHidden!!.isFaceDown)
            assertNotEquals("Spectator must not see face-down exile identity",
                "Sol Ring", spectatorHidden.name)
            assertNull("Spectator must not receive hidden art URI", spectatorHidden.revealedImageUri)
            assertEquals("Face-up exile is public to spectators",
                "Island", spectatorControl?.name)
        } catch (failure: AssertionError) {
            violations.add(failure.message ?: "Spectator projection assertion failed")
        }
        assertTrue("Face-down exile projection violations: ${violations.joinToString(" | ")}",
            violations.isEmpty())
    }

    /**
     * A card-specific "may look at" grant in exile must preserve authorized
     * access without exposing an unrelated hidden exile or a spectator view.
     * This is the positive companion to the fail-closed owner/privacy cases.
     * Accept either the full identity or the dedicated revealedName field:
     * the server is free to render an authorized face-down card as a masked
     * shell with an explicitly permitted reveal.
     */
    @Test fun explicitExileLookGrantIsCardScopedAndNotPublic() {
        val d = setup()
        val granted = d.putCardInExile(d.player2, "Sol Ring")
        val unrelated = d.putCardInExile(d.player2, "Grizzly Bears")
        val ownerGranted = d.putCardInExile(d.player2, "Island")
        d.addComponent(granted, com.wingedsheep.engine.state.components.identity.FaceDownComponent)
        d.addComponent(unrelated, com.wingedsheep.engine.state.components.identity.FaceDownComponent)
        d.addComponent(ownerGranted, com.wingedsheep.engine.state.components.identity.FaceDownComponent)
        d.addComponent(granted,
            com.wingedsheep.engine.state.components.identity.MayLookAtInExileComponent.to(d.player1))
        d.addComponent(ownerGranted,
            com.wingedsheep.engine.state.components.identity.MayLookAtInExileComponent.to(d.player2))

        val visibility = com.wingedsheep.engine.view.Visibility(
            d.cardRegistry, conditionEvaluator = d.services.conditionEvaluator,
        )
        val transformer = com.wingedsheep.engine.view.ClientStateTransformer(
            d.cardRegistry, predicateEvaluator = d.services.predicateEvaluator,
        )
        assertFalse("Explicit grant must permit the named viewer",
            visibility.isCardIdentityHiddenFrom(d.state, granted, d.player1))
        assertTrue("The same viewer cannot inspect unrelated face-down exile",
            visibility.isCardIdentityHiddenFrom(d.state, unrelated, d.player1))
        assertFalse("Explicit card-scoped exile grant must also work for the owner",
            visibility.isCardIdentityHiddenFrom(d.state, ownerGranted, d.player2))
        assertTrue("An owner-only exile grant must not authorize the opponent",
            visibility.isCardIdentityHiddenFrom(d.state, ownerGranted, d.player1))
        val viewer = transformer.transform(d.state, d.player1)
        val permitted = viewer.cards[granted] ?: throw AssertionError("Granted card missing")
        assertTrue("Granted identity must be available to the authorized viewer",
            permitted.name == "Sol Ring" || permitted.revealedName == "Sol Ring")
        val other = viewer.cards[unrelated] ?: throw AssertionError("Unrelated exile missing")
        assertNotEquals("Unrelated hidden card identity leaked", "Grizzly Bears", other.name)
        assertNotEquals("Unrelated hidden reveal leaked", "Grizzly Bears", other.revealedName)
        assertNull("Unrelated hidden art URI leaked", other.revealedImageUri)
        val ownerOnlyToOpponent = viewer.cards[ownerGranted]
            ?: throw AssertionError("Owner-granted exile missing from opponent snapshot")
        assertNotEquals("Owner-only exile grant leaked to opponent",
            "Island", ownerOnlyToOpponent.name)
        assertNotEquals("Owner-only exile reveal leaked to opponent",
            "Island", ownerOnlyToOpponent.revealedName)
        assertNull("Owner-only exile art URI leaked to opponent",
            ownerOnlyToOpponent.revealedImageUri)
        val ownerView = transformer.transform(d.state, d.player2)
        val ownerOnly = ownerView.cards[ownerGranted]
            ?: throw AssertionError("Owner-granted exile missing from owner snapshot")
        assertTrue("Card-scoped owner exile grant must reveal its own card",
            ownerOnly.name == "Island" || ownerOnly.revealedName == "Island")
        val spectator = transformer.transform(d.state, d.player1, isSpectator = true)
        for ((cardId, realName) in listOf(granted to "Sol Ring", unrelated to "Grizzly Bears", ownerGranted to "Island")) {
            val masked = spectator.cards[cardId] ?: throw AssertionError("Spectator card missing")
            assertNotEquals("Spectator must not receive an explicit look grant", realName, masked.name)
            assertNotEquals("Spectator reveal must remain private", realName, masked.revealedName)
            assertNull("Spectator must not receive any hidden art URI",
                masked.revealedImageUri)
        }
    }

    @Test fun unchosenFaceDownOpponentExileRemainsMaskedAfterProcessingOtherTwo() {
        val d = setup()
        val hidden = d.putCardInExile(d.player2, "Sol Ring")
        d.addComponent(hidden, com.wingedsheep.engine.state.components.identity.FaceDownComponent)
        d.putCardInExile(d.player2, "Island")
        d.putCardInExile(d.player2, "Grizzly Bears")

        val bears = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.GREEN)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, bears).error)
        val nullifier = d.putCardInHand(d.player1, "Ulamog's Nullifier")
        d.giveMana(d.player1, Color.BLUE)
        d.giveMana(d.player1, Color.BLACK)
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, nullifier).error)

        assertNull(d.bothPass().error)
        assertTrue(d.state.pendingDecision is com.wingedsheep.engine.core.ChooseTargetsDecision)
        d.decide(Choices(targets = listOf(bears)))
        assertNull(d.bothPass().error)
        assertTrue(d.state.pendingDecision is com.wingedsheep.engine.core.YesNoDecision)
        d.decide(Choices(yes = true))
        repeat(3) {
            if (d.state.pendingDecision == null && d.state.stack.isNotEmpty()) {
                assertNull(d.bothPass().error)
            }
        }
        val choice = d.state.pendingDecision as? com.wingedsheep.engine.core.SelectCardsDecision
            ?: throw AssertionError("Three eligible exiles must require a real selection")
        assertTrue(hidden in choice.options)
        val visibility = com.wingedsheep.engine.view.Visibility(
            d.cardRegistry, conditionEvaluator = d.services.conditionEvaluator,
        )
        val masker = com.wingedsheep.engine.view.DecisionMasker(visibility)
        // Check the actual server snapshot projection as well as the pending
        // decision metadata: the Android card picker resolves IDs through this
        // snapshot, so masking only SelectCardsDecision.cardInfo is insufficient.
        val stateTransformer = com.wingedsheep.engine.view.ClientStateTransformer(
            d.cardRegistry, predicateEvaluator = d.services.predicateEvaluator,
        )
        for (viewer in listOf(d.player1, d.player2)) {
            val masked = masker.maskFor(choice, d.state, viewer)
                as com.wingedsheep.engine.core.SelectCardsDecision
            val info = masked.cardInfo?.get(hidden)
                ?: throw AssertionError("Hidden card must have player-facing masked metadata")
            assertNotEquals("Sol Ring", info.name)
            assertEquals("", info.manaCost)

            val snapshotCard = stateTransformer.transform(d.state, viewer).cards[hidden]
                ?: throw AssertionError("Hidden exile must be represented in the client snapshot")
            assertNotEquals("Client snapshot must not reveal hidden card identity",
                "Sol Ring", snapshotCard.name)
            assertEquals("Client snapshot must mask mana cost", "", snapshotCard.manaCost)
            assertEquals("Client snapshot must mask type line", "", snapshotCard.typeLine)
            assertEquals("Client snapshot must mask rules text", "", snapshotCard.oracleText)
            assertNull("Client snapshot must mask hidden art URI",
                snapshotCard.revealedImageUri)
        }

        // Deliberately choose the two face-up cards; leave the face-down one.
        d.decide(Choices(pick = listOf("Island", "Grizzly Bears")))
        d.resolveAll()
        assertTrue("The targeted spell must be countered after processing two cards",
            d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
        assertTrue("The unchosen hidden entity must remain in exile",
            d.getExile(d.player2).contains(hidden))
        assertEquals(listOf("Sol Ring"), d.getExileCardNames(d.player2))
        assertEquals(listOf("Grizzly Bears", "Island"), d.getGraveyardCardNames(d.player2).sorted())
        for (viewer in listOf(d.player1, d.player2)) {
            assertTrue("Unchosen face-down exile must remain hidden after processing",
                visibility.isCardIdentityHiddenFrom(d.state, hidden, viewer))
        }
    }

    /**
     * Positive non-exile control for engine blocker #492: a face-down permanent
     * on the battlefield is different from a face-down card in exile (CR 708.5).
     * Its controller may inspect its printed identity; other players and
     * spectators must not. This projection-only fixture deliberately does not
     * claim to test casting a real morph spell.
     *
     * Any engine-side exile masking fix must preserve this battlefield rule.
     */
    @Test fun faceDownBattlefieldControllerStillSeesOwnCard() {
        val d = setup()
        val permanent = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.addComponent(permanent, com.wingedsheep.engine.state.components.identity.FaceDownComponent)
        val transformer = com.wingedsheep.engine.view.ClientStateTransformer(
            d.cardRegistry, predicateEvaluator = d.services.predicateEvaluator,
        )

        val controller = transformer.transform(d.state, d.player1).cards[permanent]
            ?: throw AssertionError("Controller battlefield card missing")
        assertTrue("Controller sees face-down status", controller.isFaceDown)
        assertEquals("Controller may inspect their own face-down permanent",
            "Grizzly Bears", controller.name)

        val opponent = transformer.transform(d.state, d.player2).cards[permanent]
            ?: throw AssertionError("Opponent battlefield card missing")
        assertTrue("Opponent sees face-down status", opponent.isFaceDown)
        assertNotEquals("Opponent cannot inspect printed identity",
            "Grizzly Bears", opponent.name)
        assertEquals("Opponent cannot inspect mana cost", "", opponent.manaCost)
        assertEquals("Opponent cannot inspect Oracle text", "", opponent.oracleText)

        val spectator = transformer.transform(d.state, d.player1, isSpectator = true)
            .cards[permanent] ?: throw AssertionError("Spectator battlefield card missing")
        assertTrue("Spectator sees face-down status", spectator.isFaceDown)
        assertNotEquals("Spectator cannot inspect printed identity",
            "Grizzly Bears", spectator.name)
        assertEquals("Spectator cannot inspect Oracle text", "", spectator.oracleText)
    }

    /**
     * A real Nullifier two-card choice must honor a card-scoped exile look grant
     * without exposing an unrelated face-down card to the grantee, owner, or
     * spectator. This tests DecisionMasker, not the separate CardProjector bug.
     */
    @Test fun exileLookGrantIsCardScopedDuringActualNullifierSelection() {
        val d = setup()
        val granted = d.putCardInExile(d.player2, "Sol Ring")
        val unrelated = d.putCardInExile(d.player2, "Grizzly Bears")
        d.putCardInExile(d.player2, "Island")
        d.addComponent(granted, com.wingedsheep.engine.state.components.identity.FaceDownComponent)
        d.addComponent(unrelated, com.wingedsheep.engine.state.components.identity.FaceDownComponent)
        d.addComponent(granted,
            com.wingedsheep.engine.state.components.identity.MayLookAtInExileComponent.to(d.player1))

        val bears = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.GREEN)
        d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, bears).error)
        val nullifier = d.putCardInHand(d.player1, "Ulamog's Nullifier")
        d.giveMana(d.player1, Color.BLUE)
        d.giveMana(d.player1, Color.BLACK)
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, nullifier).error)
        assertNull(d.bothPass().error)
        assertTrue(d.state.pendingDecision is com.wingedsheep.engine.core.ChooseTargetsDecision)
        d.decide(Choices(targets = listOf(bears)))
        assertNull(d.bothPass().error)
        assertTrue(d.state.pendingDecision is com.wingedsheep.engine.core.YesNoDecision)
        d.decide(Choices(yes = true))
        repeat(3) {
            if (d.state.pendingDecision == null && d.state.stack.isNotEmpty()) {
                assertNull(d.bothPass().error)
            }
        }
        val choice = d.state.pendingDecision as? com.wingedsheep.engine.core.SelectCardsDecision
            ?: throw AssertionError("Nullifier must present a real two-card selection")
        assertTrue(granted in choice.options)
        assertTrue(unrelated in choice.options)
        val visibility = com.wingedsheep.engine.view.Visibility(
            d.cardRegistry, conditionEvaluator = d.services.conditionEvaluator)
        val masker = com.wingedsheep.engine.view.DecisionMasker(visibility)
        assertFalse("Grant must authorize its named viewer",
            visibility.isCardIdentityHiddenFrom(d.state, granted, d.player1))
        assertTrue("Unrelated hidden exile stays concealed",
            visibility.isCardIdentityHiddenFrom(d.state, unrelated, d.player1))
        val permitted = masker.maskFor(choice, d.state, d.player1)
            as com.wingedsheep.engine.core.SelectCardsDecision
        assertEquals("Granted card identity must be available in picker",
            "Sol Ring", permitted.cardInfo?.get(granted)?.name)
        assertNotEquals("Grant must not expose unrelated face-down exile",
            "Grizzly Bears", permitted.cardInfo?.get(unrelated)?.name)
        val owner = masker.maskFor(choice, d.state, d.player2)
            as com.wingedsheep.engine.core.SelectCardsDecision
        assertNotEquals("Owner has no implicit right to inspect face-down exile",
            "Sol Ring", owner.cardInfo?.get(granted)?.name)
        assertNotEquals("Owner cannot inspect unrelated hidden exile",
            "Grizzly Bears", owner.cardInfo?.get(unrelated)?.name)
        val spectator = masker.maskFor(choice, d.state, d.player1, isSpectator = true)
            as com.wingedsheep.engine.core.SelectCardsDecision
        assertNotEquals("Spectator must not inherit controller's look grant",
            "Sol Ring", spectator.cardInfo?.get(granted)?.name)
        assertNotEquals("Spectator cannot inspect unrelated hidden exile",
            "Grizzly Bears", spectator.cardInfo?.get(unrelated)?.name)

        d.decide(Choices(pick = listOf("Sol Ring", "Island")))
        d.resolveAll()
        assertTrue("Two processed exiles must counter the targeted spell",
            d.permanentsNamed(d.player1, "Grizzly Bears").isEmpty())
        assertEquals(listOf("Grizzly Bears"), d.getExileCardNames(d.player2))
        assertEquals(listOf("Island", "Sol Ring"), d.getGraveyardCardNames(d.player2).sorted())
    }

    /**
     * Positive public-zone control for the upstream face-down exile privacy fix.
     * Exile is public unless a specific card is face-down; masking every exiled
     * card to repair #492 would be a separate rules and client regression.
     */
    @Test fun ordinaryFaceUpExileRemainsPublicToPlayersAndSpectators() {
        val d = setup()
        val cardId = d.putCardInExile(d.player2, "Sol Ring")
        val transformer = com.wingedsheep.engine.view.ClientStateTransformer(
            d.cardRegistry, predicateEvaluator = d.services.predicateEvaluator,
        )
        for ((viewer, spectator) in listOf(
            d.player1 to false, d.player2 to false, d.player1 to true,
        )) {
            val card = transformer.transform(d.state, viewer, isSpectator = spectator)
                .cards[cardId] ?: throw AssertionError("Face-up exiled card missing")
            assertFalse("Face-up exile must not be marked face-down", card.isFaceDown)
            assertEquals("Face-up exile identity must be public", "Sol Ring", card.name)
            assertEquals("Face-up exile type must be public", "Artifact", card.typeLine)
            assertEquals("Face-up exile mana cost must be public", "{1}", card.manaCost)
        }
    }
}
