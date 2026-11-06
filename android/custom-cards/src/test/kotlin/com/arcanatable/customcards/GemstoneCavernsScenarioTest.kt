package com.arcanatable.customcards

import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Gemstone Caverns (Argentum library card, as the app plays it) — the opening-hand action:
 * "If this card is in your opening hand and you're not the starting player, you may begin the game
 * with Gemstone Caverns on the battlefield with a luck counter on it. If you do, exile a card from
 * your hand. {T}: Add {C}. If Gemstone Caverns has a luck counter on it, instead add one mana of any
 * color." The app's offline host runs the real mulligan phase (`skipMulligans = false`), so these
 * tests drive the same pregame path: keep hands, then the begin-the-game choice.
 */
class GemstoneCavernsScenarioTest {

    private val caverns = "Gemstone Caverns"

    private fun driver() = CardPool.driver(caverns, "Lightning Bolt", "Island")

    /** Puts one Gemstone Caverns into [player]'s opening hand (swapping out an Island). */
    private fun GameTestDriver.dealCaverns(player: EntityId) {
        var s = state
        val wanted = s.getLibrary(player).firstOrNull { s.getEntity(it)?.get<CardComponent>()?.name == caverns } ?: return
        val outgoing = s.getHand(player).first { s.getEntity(it)?.get<CardComponent>()?.name != caverns }
        s = s.removeFromZone(ZoneKey(player, Zone.LIBRARY), wanted).addToZone(ZoneKey(player, Zone.HAND), wanted)
        s = s.removeFromZone(ZoneKey(player, Zone.HAND), outgoing).addToZone(ZoneKey(player, Zone.LIBRARY), outgoing)
        replaceState(s)
    }

    private fun GameTestDriver.luck(id: EntityId) =
        state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.LUCK) ?: 0

    private fun deckWithCaverns() = Deck.of(caverns to 1, "Island" to 39)

    @Test
    fun onTheDrawItBeginsOnTheBattlefieldWithALuckCounterAndTapsForAnyColor() {
        val d = driver()
        d.initGame(Deck.of("Island" to 40), deckWithCaverns(), skipMulligans = false)
        d.dealCaverns(d.player2)
        assertNull(d.submit(KeepHand(d.player1)).error)
        assertNull(d.submit(KeepHand(d.player2)).error)

        val offer = d.state.pendingDecision
        assertTrue("pregame choice offered (was $offer)", offer is YesNoDecision)
        assertEquals(d.player2, offer!!.playerId)
        assertTrue("before the first turn begins", d.state.turnNumber <= 1 && d.state.stack.isEmpty())
        assertNull(d.submitYesNo(d.player2, true).error)

        val exile = d.state.pendingDecision as SelectCardsDecision
        val handBefore = d.getHand(d.player2).size
        val gone = exile.options.first()
        assertNull(d.submitCardSelection(d.player2, listOf(gone)).error)
        assertEquals(listOf(gone), d.state.getZone(ZoneKey(d.player2, Zone.EXILE)))
        assertEquals("one card exiled from hand", handBefore - 1, d.getHand(d.player2).size)

        val onField = d.permanentsNamed(d.player2, caverns).single()
        assertEquals(1, d.luck(onField))

        // Player 2's first turn: the Caverns is the only untapped source and pays {R}.
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, d.player2)
        for (land in d.getLands(d.player2)) if (land != onField) d.tapPermanent(land)
        val bolt = d.putCardInHand(d.player2, "Lightning Bolt")
        assertNull("luck counter: any color", d.castSpell(d.player2, bolt, listOf(d.player1)).error)
    }

    @Test
    fun theStartingPlayerIsNotOffered() {
        val d = driver()
        d.initGame(deckWithCaverns(), Deck.of("Island" to 40), skipMulligans = false)
        d.dealCaverns(d.player1)
        assertNull(d.submit(KeepHand(d.player1)).error)
        assertNull(d.submit(KeepHand(d.player2)).error)
        assertNull("no choice for the starting player", d.state.pendingDecision as? YesNoDecision)
        assertTrue(d.zoneNames(d.player1, Zone.HAND).contains(caverns))
        assertTrue(d.permanentsNamed(d.player1, caverns).isEmpty())
    }

    @Test
    fun decliningKeepsItInHandAndExilesNothing() {
        val d = driver()
        d.initGame(Deck.of("Island" to 40), deckWithCaverns(), skipMulligans = false)
        d.dealCaverns(d.player2)
        d.submit(KeepHand(d.player1)); d.submit(KeepHand(d.player2))
        assertTrue(d.state.pendingDecision is YesNoDecision)
        assertNull(d.submitYesNo(d.player2, false).error)
        assertTrue(d.zoneNames(d.player2, Zone.HAND).contains(caverns))
        assertTrue(d.state.getZone(ZoneKey(d.player2, Zone.EXILE)).isEmpty())
        assertEquals(7, d.getHand(d.player2).size)
    }

    @Test
    fun notInTheOpeningHandMeansNoOffer() {
        val d = driver()
        d.initGame(Deck.of("Island" to 40), deckWithCaverns(), skipMulligans = false)
        // Make sure it is in the library, not the hand.
        val inHand = d.getHand(d.player2).firstOrNull { d.getCardName(it) == caverns }
        if (inHand != null) {
            val s = d.state.removeFromZone(ZoneKey(d.player2, Zone.HAND), inHand).addToZone(ZoneKey(d.player2, Zone.LIBRARY), inHand)
            d.replaceState(s)
        }
        d.submit(KeepHand(d.player1)); d.submit(KeepHand(d.player2))
        assertNull(d.state.pendingDecision as? YesNoDecision)
    }

    @Test
    fun playedNormallyWithoutALuckCounterItTapsOnlyForColorless() {
        val d = scenario(caverns, "Lightning Bolt")
        val p = d.player1
        for (land in d.getLands(p)) d.tapPermanent(land)
        val cav = d.putPermanentOnBattlefield(p, caverns)
        assertEquals(0, d.luck(cav))
        val bolt = d.putCardInHand(p, "Lightning Bolt")
        assertNotNull("{C} can't pay {R}", d.castSpell(p, bolt, listOf(d.player2)).error)
    }

    @Test
    fun fourPlayerGameEveryNonStartingSeatIsOfferedAndTheStarterIsNot() {
        val d = driver()
        val seats = d.initMultiplayer(List(4) { deckWithCaverns() }, skipMulligans = false)
        seats.forEach { d.dealCaverns(it) }
        seats.forEach { assertNull(d.submit(KeepHand(it)).error) }
        val asked = mutableListOf<EntityId>()
        var guard = 0
        while (guard++ < 20) {
            when (val decision = d.state.pendingDecision) {
                is YesNoDecision -> { asked += decision.playerId; d.submitYesNo(decision.playerId, true) }
                is SelectCardsDecision -> d.submitCardSelection(decision.playerId, listOf(decision.options.first()))
                else -> break
            }
        }
        assertEquals("seats 2–4 are asked, the starting player is not", seats.drop(1).toSet(), asked.toSet())
        assertTrue(d.permanentsNamed(seats[0], caverns).isEmpty())
        for (seat in seats.drop(1)) {
            val cav = d.permanentsNamed(seat, caverns).single()
            assertEquals(1, d.luck(cav))
            assertEquals(1, d.state.getZone(ZoneKey(seat, Zone.EXILE)).size)
        }
    }
}
