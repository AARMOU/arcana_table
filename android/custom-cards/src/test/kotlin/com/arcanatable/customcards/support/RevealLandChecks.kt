package com.arcanatable.customcards.support

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.RevealedToComponent
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import org.junit.Assert.*

/**
 * Shared rules checks for the "as this land enters, you may reveal an X or Y card from your hand;
 * if you don't, it enters tapped" lands (Port Town, Fortified Village, Furycalm Snarl, …). Each
 * card's own scenario test calls these with its two land types and one land type that must not
 * count, so the whole cycle is held to the same executable rules.
 */
object RevealLandChecks {

    /** "Return target land card from your graveyard to the battlefield tapped." */
    val ReturnLandTappedTest = card("Return Land Tapped Test") {
        manaCost = "{0}"
        typeLine = "Sorcery"
        oracleText = "Return target land card from your graveyard to the battlefield tapped."
        spell {
            val land = target(TargetFilter(GameObjectFilter.Land.ownedByYou(), zone = Zone.GRAVEYARD))
            effect = Effects.PutOntoBattlefield(land, tapped = true)
        }
        metadata { rarity = Rarity.COMMON; collectorNumber = "1" }
    }

    private fun GameTestDriver.clearHand(player: EntityId) {
        for (card in getHand(player).toList()) moveToExile(card)
    }

    private fun GameTestDriver.pendingReveal(): SelectCardsDecision? = state.pendingDecision as? SelectCardsDecision

    /** Plays [land] from hand and answers the reveal question with [reveal] (null = decline). */
    fun GameTestDriver.playRevealLand(player: EntityId, land: EntityId, reveal: EntityId?): SelectCardsDecision? {
        assertNull(playLand(player, land).error)
        val decision = pendingReveal()
        if (decision != null) {
            assertEquals("the land's controller decides", player, decision.playerId)
            assertEquals("the reveal is optional", 0, decision.minSelections)
            assertEquals(1, decision.maxSelections)
            assertTrue("decided as it enters: nothing uses the stack", state.stack.isEmpty())
            assertNull(submitCardSelection(player, listOfNotNull(reveal)).error)
        }
        return decision
    }

    fun revealingAMatchingCardEntersUntapped(d: GameTestDriver, landName: String, typeA: String, typeB: String) {
        for (type in listOf(typeA, typeB)) {
            val p = d.player1
            d.clearHand(p)
            val before = d.permanentsNamed(p, landName).toSet()
            val land = d.putCardInHand(p, landName)
            val shown = d.putCardInHand(p, type)
            val decision = d.playRevealLand(p, land, shown)
            assertNotNull("$landName asks to reveal", decision)
            assertTrue("$type is a legal reveal", shown in decision!!.options)
            val entered = (d.permanentsNamed(p, landName).toSet() - before).single()
            assertFalse("revealing $type: $landName enters untapped", d.isTapped(entered))
            assertTrue("the revealed card stays in hand", shown in d.getHand(p))
            val seen = d.state.getEntity(shown)?.get<RevealedToComponent>()?.playerIds.orEmpty()
            assertTrue("revealed to every player", d.state.turnOrder.all { it in seen })
            // Next land drop on a later turn for the second type.
            d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN, d.player2)
            d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN, d.player1)
        }
    }

    fun decliningEntersTapped(d: GameTestDriver, landName: String, typeA: String) {
        val p = d.player1
        d.clearHand(p)
        val land = d.putCardInHand(p, landName)
        d.putCardInHand(p, typeA)
        assertNotNull(d.playRevealLand(p, land, reveal = null))
        assertTrue("declined: enters tapped", d.isTapped(d.permanentsNamed(p, landName).single()))
    }

    fun nonMatchingCardsAreNotOffered(d: GameTestDriver, landName: String, wrongType: String) {
        val p = d.player1
        d.clearHand(p)
        val land = d.putCardInHand(p, landName)
        val wrong = d.putCardInHand(p, wrongType)
        val twin = d.putCardInHand(p, landName)
        val decision = d.playRevealLand(p, land, reveal = null)
        // A hand with no match is still asked (so the question reveals nothing), with nothing to pick.
        assertNotNull("still asked when nothing matches", decision)
        assertFalse("$wrongType does not count", wrong in decision!!.options)
        assertFalse("$landName itself is neither type", twin in decision.options)
        assertTrue(d.isTapped(d.permanentsNamed(p, landName).single()))
    }

    fun emptyHandEntersTappedWithoutAQuestion(d: GameTestDriver, landName: String) {
        val p = d.player1
        d.clearHand(p)
        val land = d.putCardInHand(p, landName)
        assertNull(d.playLand(p, land).error)
        assertNull("no question with an empty hand", d.state.pendingDecision)
        assertTrue(d.isTapped(d.permanentsNamed(p, landName).single()))
    }

    fun putOntoTheBattlefieldTappedStaysTapped(d: GameTestDriver, landName: String, typeA: String) {
        val p = d.player1
        d.clearHand(p)
        val inGraveyard = d.putCardInGraveyard(p, landName)
        val shown = d.putCardInHand(p, typeA)
        val spell = d.putCardInHand(p, "Return Land Tapped Test")
        assertNull(d.castSpellWithTargets(p, spell, listOf(
            com.wingedsheep.engine.state.components.stack.ChosenTarget.Card(inGraveyard, p, Zone.GRAVEYARD)
        )).error)
        var guard = 0
        while (guard++ < 30) {
            val decision = d.state.pendingDecision
            when {
                decision is SelectCardsDecision && shown in decision.options -> d.submitCardSelection(p, listOf(shown))
                decision != null -> d.decide()
                d.state.stack.isNotEmpty() -> d.bothPass()
                else -> break
            }
        }
        assertTrue("an instruction to enter tapped wins even after a reveal", d.isTapped(d.permanentsNamed(p, landName).single()))
    }

    /** Four-player game: the reveal is shown to every seat, and only the land's controller is asked. */
    fun fourPlayerRevealIsPublic(landName: String, type: String, vararg extra: com.wingedsheep.sdk.model.CardDefinition) {
        val d = CardPool.driver(landName, type).apply { registerCards(extra.toList()) }
        val seats = d.initMultiplayer(List(4) { com.wingedsheep.sdk.model.Deck.of("Wastes" to 40) })
        d.passPriorityUntil(com.wingedsheep.sdk.core.Step.PRECOMBAT_MAIN)
        val p = seats[0]
        d.clearHand(p)
        val land = d.putCardInHand(p, landName)
        val shown = d.putCardInHand(p, type)
        assertNotNull(d.playRevealLand(p, land, shown))
        assertFalse(d.isTapped(d.permanentsNamed(p, landName).single()))
        val seen = d.state.getEntity(shown)?.get<RevealedToComponent>()?.playerIds.orEmpty()
        assertEquals("all four seats saw it", seats.toSet(), seats.filter { it in seen }.toSet())
    }
}

