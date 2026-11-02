package com.arcanatable.customcards

import com.arcanatable.customcards.cards.DarksteelMonolith
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Darksteel Monolith (CMM #743), Oracle: "Indestructible / Once each turn, you may pay {0} rather
 * than pay the mana cost for a colorless spell you cast from your hand."
 *
 * Rulings exercised (WotC Commander Masters release notes, Darksteel Monolith; CR 118.9): paying {0}
 * is an alternative cost, so additional costs still apply; the permission doesn't change when a
 * spell can be cast, so another player's turn allows only instants (and flash); "once each turn"
 * includes opponents' turns; paying a spell's cost normally doesn't use the permission; each
 * Monolith gives its own use. Opponent-turn case from the independent #443 finding.
 */
class DarksteelMonolithScenarioTest {

    private val monolith = "Darksteel Monolith"

    private fun GameTestDriver.freeCast(player: EntityId, card: EntityId, targets: List<ChosenTarget> = emptyList()) =
        submit(CastSpell(player, card, targets = targets, useWithoutPayingManaCost = true))

    /** Player 1 controls a Monolith; no lands anywhere, so a cast without mana only works for free. */
    private fun withMonolith(vararg library: String): GameTestDriver =
        customScenario(DarksteelMonolith, *library).also { it.putPermanentOnBattlefield(it.player1, monolith) }

    @Test
    fun costsEightAndIsIndestructibleButCanBeExiled() {
        val d = customScenario(DarksteelMonolith, "Shatter", "Revoke Existence")
        val p = d.player1
        val card = d.putCardInHand(p, monolith)
        d.giveColorlessMana(p, 7)
        assertNotNull("seven mana is not enough", d.castSpell(p, card).error)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, card).error)
        d.resolveAll()
        val permanent = d.findPermanent(p, monolith)!!
        assertTrue(d.projected.hasKeyword(permanent, Keyword.INDESTRUCTIBLE))

        val shatter = d.putCardInHand(p, "Shatter")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, shatter, listOf(permanent)).error)
        d.resolveAll()
        assertTrue("destroy doesn't remove it", d.getPermanents(p).contains(permanent))

        val revoke = d.putCardInHand(p, "Revoke Existence")
        d.giveMana(p, Color.WHITE)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, revoke, listOf(permanent)).error)
        d.resolveAll()
        assertTrue("exile does", d.zoneNames(p, Zone.EXILE).contains(monolith))
    }

    @Test
    fun oneFreeColorlessSpellFromHandThenTheTurnsUseIsGone() {
        val d = withMonolith("Sol Ring")
        val p = d.player1
        assertNull(d.freeCast(p, d.putCardInHand(p, "Sol Ring")).error)
        d.resolveAll()
        val second = d.putCardInHand(p, "Sol Ring")
        assertNotNull("only once each turn", d.freeCast(p, second).error)
        assertEquals(1, d.permanentsNamed(p, "Sol Ring").size)
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Sol Ring"))
    }

    @Test
    fun usableOnAnOpponentsTurnForAnInstantAndRefreshedEachTurn() {
        val d = withMonolith("Sol Ring", "Scour from Existence", "Forest")
        val p = d.player1
        val opponent = d.player2
        assertNull("own turn: use it", d.freeCast(p, d.putCardInHand(p, "Sol Ring")).error)
        d.resolveAll()

        val land = d.putPermanentOnBattlefield(opponent, "Forest")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        assertNull(d.passPriority(opponent).error)
        assertEquals("player 1 has priority on the opponent's turn", p, d.state.priorityPlayerId)

        val ring = d.putCardInHand(p, "Sol Ring")
        assertNotNull("timing unchanged: no artifact on the opponent's turn", d.freeCast(p, ring).error)
        val scour = d.putCardInHand(p, "Scour from Existence")
        assertNull(
            "once EACH turn: a fresh use on the opponent's turn, for an instant",
            d.freeCast(p, scour, listOf(ChosenTarget.Permanent(land))).error,
        )
        d.resolveAll()
        assertFalse(d.getPermanents(opponent).contains(land))

        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        assertNull("fresh use again on player 1's next turn", d.freeCast(p, ring).error)
        d.resolveAll()
        assertEquals(2, d.permanentsNamed(p, "Sol Ring").size)
    }

    @Test
    fun opponentsTurnUseIsAlsoOnlyOnce() {
        val d = withMonolith("Scour from Existence", "Forest")
        val p = d.player1
        val opponent = d.player2
        val first = d.putPermanentOnBattlefield(opponent, "Forest")
        val second = d.putPermanentOnBattlefield(opponent, "Forest")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        d.passPriority(opponent)
        assertNull(d.freeCast(p, d.putCardInHand(p, "Scour from Existence"), listOf(ChosenTarget.Permanent(first))).error)
        d.resolveAll()
        d.passPriority(opponent)
        val again = d.putCardInHand(p, "Scour from Existence")
        assertNotNull(d.freeCast(p, again, listOf(ChosenTarget.Permanent(second))).error)
        assertTrue(d.getPermanents(opponent).contains(second))
    }

    @Test
    fun payingNormallyFirstDoesNotUseItUp() {
        val d = withMonolith("Sol Ring", "Kozilek's Channeler")
        val p = d.player1
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, d.putCardInHand(p, "Sol Ring")).error)
        d.resolveAll()
        assertNull(d.freeCast(p, d.putCardInHand(p, "Kozilek's Channeler")).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Kozilek's Channeler"))
    }

    @Test
    fun onlyColorlessSpellsColorlessCreaturesIncluded() {
        val d = withMonolith("Grizzly Bears", "Kozilek's Channeler")
        val p = d.player1
        assertNotNull("colored spell refused", d.freeCast(p, d.putCardInHand(p, "Grizzly Bears")).error)
        assertNull("colorless nonartifact creature allowed", d.freeCast(p, d.putCardInHand(p, "Kozilek's Channeler")).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Kozilek's Channeler"))
        assertNull(d.findPermanent(p, "Grizzly Bears"))
    }

    @Test
    fun onlyFromYourHandAndOnlyForItsController() {
        val d = withMonolith("Sol Ring")
        val p = d.player1
        val buried = d.putCardInGraveyard(p, "Sol Ring")
        assertNotNull("not from the graveyard", d.freeCast(p, buried).error)
        assertNotNull("not from exile", d.freeCast(p, d.putCardInExile(p, "Sol Ring")).error)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertNotNull("not for the opponent", d.freeCast(d.player2, d.putCardInHand(d.player2, "Sol Ring")).error)
        assertTrue(d.getGraveyard(p).contains(buried))
    }

    @Test
    fun twoMonolithsGiveTwoSeparateUses() {
        val d = withMonolith("Sol Ring")
        val p = d.player1
        d.putPermanentOnBattlefield(p, monolith)
        repeat(2) {
            assertNull(d.freeCast(p, d.putCardInHand(p, "Sol Ring")).error)
            d.resolveAll()
        }
        assertNotNull(d.freeCast(p, d.putCardInHand(p, "Sol Ring")).error)
        assertEquals(2, d.permanentsNamed(p, "Sol Ring").size)
    }

    @Test
    fun additionalCostsStillHaveToBePaid() {
        val d = withMonolith("Titan's Presence", "Grizzly Bears")
        val p = d.player1
        val target = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        // Titan's Presence requires revealing a colorless creature card; the hand has none.
        val spell = d.putCardInHand(p, "Titan's Presence")
        assertNotNull(d.freeCast(p, spell, listOf(ChosenTarget.Permanent(target))).error)
        assertTrue(d.getPermanents(d.player2).contains(target))
        assertTrue(d.zoneNames(p, Zone.HAND).contains("Titan's Presence"))
    }
}
