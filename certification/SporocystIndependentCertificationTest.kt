package com.arcanatable.customcards

// Independently authored trusted-main checks, written from the exact Oracle text only:
// Sporocyst {X}{X}{G}, 0/0 Creature — Tyranid. "Ravenous (This creature enters with X +1/+1
// counters on it. If X is 5 or more, draw a card when it enters.) Defender. Spore Chimney —
// When this creature enters, search your library for up to X basic land cards, put them onto
// the battlefield tapped, then shuffle." Never copied from the Copilot PR.
import com.arcanatable.customcards.cards.Sporocyst
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import org.junit.Assert.*
import org.junit.Test

class SporocystIndependentCertificationTest {
    private val takeLands = Choices(pick = listOf("Plains", "Forest", "Command Tower"))

    private fun GameTestDriver.cast(x: Int): com.wingedsheep.sdk.model.EntityId {
        val spore = putCardInHand(player1, "Sporocyst")
        giveMana(player1, Color.GREEN)
        giveColorlessMana(player1, 2 * x)
        assertNull(castXSpell(player1, spore, x).error)
        return spore
    }

    @Test
    fun xTwoEntersAsTwoTwoFetchesTwoTappedBasicsAndDrawsNothing() {
        val d = customScenario(Sporocyst)
        val p = d.player1
        val hand = d.getHand(p).size
        val lands = d.getLands(p).toSet()
        val spore = d.cast(2)
        d.resolveAll(takeLands)
        assertEquals(2, d.projected.getPower(spore))
        assertEquals(2, d.projected.getToughness(spore))
        assertTrue(d.projected.hasKeyword(spore, Keyword.DEFENDER))
        val fetched = d.getLands(p).filterNot { it in lands }
        assertEquals(2, fetched.size)
        assertTrue(fetched.all { d.isTapped(it) })
        assertEquals(hand, d.getHand(p).size)
    }

    @Test
    fun xFiveDrawsOneCardAndFetchesUpToFive() {
        val d = customScenario(Sporocyst)
        val p = d.player1
        val hand = d.getHand(p).size
        val lands = d.getLands(p).size
        val spore = d.cast(5)
        d.resolveAll(takeLands)
        assertEquals(5, d.projected.getPower(spore))
        assertEquals(5, d.projected.getToughness(spore))
        assertEquals(hand + 1, d.getHand(p).size)
        assertEquals(lands + 5, d.getLands(p).size)
    }

    @Test
    fun sporeChimneyNeverTakesNonbasicLandsOrMoreThanX() {
        val d = customScenario(Sporocyst, "Command Tower", "Forest")
        val p = d.player1
        d.putCardOnTopOfLibrary(p, "Command Tower")
        d.putCardOnTopOfLibrary(p, "Forest")
        val lands = d.getLands(p).size
        d.cast(1)
        d.resolveAll(takeLands)
        assertEquals(lands + 1, d.getLands(p).size)
        assertTrue(d.zoneNames(p, Zone.LIBRARY).contains("Command Tower"))
        assertFalse(d.zoneNames(p, Zone.BATTLEFIELD).contains("Command Tower"))
    }

    @Test
    fun xZeroDiesAsZeroZeroAndFetchesNothing() {
        val d = customScenario(Sporocyst)
        val p = d.player1
        val lands = d.getLands(p).size
        val spore = d.cast(0)
        d.resolveAll(takeLands)
        assertFalse(d.getPermanents(p).contains(spore))
        assertTrue(d.zoneNames(p, Zone.GRAVEYARD).contains("Sporocyst"))
        assertEquals(lands, d.getLands(p).size)
    }

    @Test
    fun defenderCannotAttackWhileAnOrdinaryCreatureCan() {
        val d = customScenario(Sporocyst, "Grizzly Bears")
        val spore = d.cast(1)
        d.resolveAll(takeLands)
        assertEquals(1, d.projected.getPower(spore))
        d.removeSummoningSickness(spore)
        // Control creature: keeps the declare-attackers step from being skipped.
        val bear = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        d.removeSummoningSickness(bear)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        val refused = d.declareAttackers(d.player1, listOf(spore), d.player2).error
        assertNotNull(refused)
        assertFalse(refused!!.contains("already been declared"))
        assertNull(d.declareAttackers(d.player1, listOf(bear), d.player2).error)
    }
}
