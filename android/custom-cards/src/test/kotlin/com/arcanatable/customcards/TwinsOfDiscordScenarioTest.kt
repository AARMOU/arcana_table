package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TwinsOfDiscord
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Twins of Discord (M3C #37), Oracle: "Whenever you attack, choose odd or even. Creatures with mana
 * value of that quality can't block this turn. (Zero is even.) / Each other colorless creature you
 * control has bloodthirst 2. (If an opponent was dealt damage this turn, that creature enters with
 * two additional +1/+1 counters on it.)"
 *
 * Library mana values: Memnite 0, Savannah Lions 1, Grizzly Bears 2, Centaur Courser 3, Hill Giant 4.
 */
class TwinsOfDiscordScenarioTest {

    private val twins = "Twins of Discord"
    private val lib = arrayOf(
        "Memnite", "Savannah Lions", "Grizzly Bears", "Centaur Courser", "Hill Giant",
        "Ornithopter", "Lightning Bolt", "Shock", "Blisterpod",
    )

    private fun GameTestDriver.counters(id: EntityId): Int =
        state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    /** Resolves the attack trigger, answering its odd/even question with [parity]. */
    private fun GameTestDriver.resolveChoosing(parity: String) {
        var asked = false
        repeat(40) {
            val decision = state.pendingDecision
            when {
                decision is ChooseOptionDecision -> {
                    assertEquals("the Twins' controller chooses", player1, decision.playerId)
                    assertEquals(listOf("Odd", "Even"), decision.options)
                    submitDecision(decision.playerId, OptionChosenResponse(decision.id, decision.options.indexOf(parity)))
                    asked = true
                }
                decision != null -> autoResolveDecision()
                state.stack.isNotEmpty() -> bothPass()
                else -> {
                    assertTrue("odd or even was asked on resolution", asked)
                    return
                }
            }
        }
        error("attack trigger did not settle")
    }

    private class Board(val d: GameTestDriver, val attacker: EntityId, val opp: Map<Int, EntityId>)

    /** Twins stays home; Grizzly Bears attacks; the opponent has one untapped creature of each mana value 0–4. */
    private fun attackWithBears(parity: String): Board {
        val d = customScenario(TwinsOfDiscord, *lib)
        d.putCreatureOnBattlefield(d.player1, twins).also { d.removeSummoningSickness(it) }
        val bears = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears").also { d.removeSummoningSickness(it) }
        val opp = mapOf(
            0 to d.putCreatureOnBattlefield(d.player2, "Memnite"),
            1 to d.putCreatureOnBattlefield(d.player2, "Savannah Lions"),
            2 to d.putCreatureOnBattlefield(d.player2, "Grizzly Bears"),
            3 to d.putCreatureOnBattlefield(d.player2, "Centaur Courser"),
            4 to d.putCreatureOnBattlefield(d.player2, "Hill Giant"),
        )
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(bears), d.player2).error)
        assertEquals("one trigger on the stack", 1, d.state.stack.size)
        d.resolveChoosing(parity)
        return Board(d, bears, opp)
    }

    private fun Board.canBlock(mv: Int): Boolean =
        d.declareBlockers(d.player2, mapOf(opp.getValue(mv) to listOf(attacker))).error == null

    // ---- attack line --------------------------------------------------------------------------

    @Test
    fun evenStopsZeroTwoAndFourFromBlockingWhileTheTwinsStaysHome() {
        val b = attackWithBears("Even")
        b.d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertFalse("mana value 0 is even", b.canBlock(0))
        assertFalse(b.canBlock(2))
        assertFalse(b.canBlock(4))
        assertTrue("odd creatures still block", b.canBlock(1))
    }

    @Test
    fun evenLeavesMvThreeAbleToBlock() {
        val b = attackWithBears("Even")
        b.d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertTrue(b.canBlock(3))
    }

    @Test
    fun oddStopsOneAndThreeFromBlocking() {
        val b = attackWithBears("Odd")
        b.d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertFalse(b.canBlock(1))
        assertFalse(b.canBlock(3))
        assertTrue("zero is even, so Memnite blocks", b.canBlock(0))
    }

    @Test
    fun oddLeavesEvenCreaturesAbleToBlock() {
        val b = attackWithBears("Odd")
        b.d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertTrue(b.canBlock(4))
    }

    @Test
    fun aCreatureArrivingAfterResolutionIsAlsoCovered() {
        // CR 611.2c: "can't block" modifies rules, not characteristics, so the group is not locked in.
        val b = attackWithBears("Even")
        val late = b.d.putCreatureOnBattlefield(b.d.player2, "Grizzly Bears")
        b.d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(b.d.declareBlockers(b.d.player2, mapOf(late to listOf(b.attacker))).error)
    }

    @Test
    fun triggersOnceForSeveralAttackers() {
        val d = customScenario(TwinsOfDiscord, *lib)
        val t = d.putCreatureOnBattlefield(d.player1, twins).also { d.removeSummoningSickness(it) }
        val a = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears").also { d.removeSummoningSickness(it) }
        val c = d.putCreatureOnBattlefield(d.player1, "Centaur Courser").also { d.removeSummoningSickness(it) }
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(t, a, c), d.player2).error)
        assertEquals(1, d.state.stack.size)
        d.resolveChoosing("Odd")
        assertTrue(d.state.stack.isEmpty())
    }

    @Test
    fun noTriggerWithoutAnAttack() {
        val d = customScenario(TwinsOfDiscord, *lib)
        d.putCreatureOnBattlefield(d.player1, twins).also { d.removeSummoningSickness(it) }
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, emptyMap()).error)
        assertTrue(d.state.stack.isEmpty())
        assertNull(d.state.pendingDecision as? ChooseOptionDecision)
    }

    @Test
    fun theRestrictionEndsWithTheTurn() {
        val b = attackWithBears("Even")
        val d = b.d
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareNoBlockers(d.player2).error)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = d.player2)
        val lions = b.opp.getValue(1).also { d.removeSummoningSickness(it) }
        // An untapped even creature (the attacking Bears is still tapped on this turn).
        val giant = d.putCreatureOnBattlefield(d.player1, "Hill Giant")
        assertNull(d.declareAttackers(d.player2, listOf(lions), d.player1).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(
            "an even creature blocks on the next turn",
            d.declareBlockers(d.player1, mapOf(giant to listOf(lions))).error,
        )
    }

    // ---- bloodthirst grant ----------------------------------------------------------------------

    private fun GameTestDriver.bolt(caster: EntityId, at: EntityId, spell: String = "Lightning Bolt") {
        giveMana(caster, Color.RED)
        assertNull(castSpell(caster, putCardInHand(caster, spell), listOf(at)).error)
        resolveAll()
    }

    private fun GameTestDriver.cast(caster: EntityId, name: String): EntityId {
        val card = putCardInHand(caster, name)
        assertNull(castSpell(caster, card).error)
        resolveAll()
        assertTrue(card in getPermanents(caster))
        return card
    }

    @Test
    fun colorlessCreatureEntersWithTwoCountersAfterAnOpponentWasDamaged() {
        val d = customScenario(TwinsOfDiscord, *lib)
        d.putCreatureOnBattlefield(d.player1, twins)
        d.bolt(d.player1, d.player2)
        val thopter = d.cast(d.player1, "Ornithopter")
        assertEquals(2, d.counters(thopter))
        assertEquals(2, d.projected.getPower(thopter))
    }

    @Test
    fun noCountersWhenNoOpponentWasDamaged() {
        val d = customScenario(TwinsOfDiscord, *lib)
        d.putCreatureOnBattlefield(d.player1, twins)
        assertEquals(0, d.counters(d.cast(d.player1, "Ornithopter")))
    }

    @Test
    fun damageToYourselfDoesNotCount() {
        val d = customScenario(TwinsOfDiscord, *lib)
        d.putCreatureOnBattlefield(d.player1, twins)
        d.bolt(d.player1, d.player1)
        assertEquals(0, d.counters(d.cast(d.player1, "Ornithopter")))
    }

    @Test
    fun coloredCreaturesGetNothing() {
        val d = customScenario(TwinsOfDiscord, *lib)
        d.putCreatureOnBattlefield(d.player1, twins)
        d.bolt(d.player1, d.player2)
        d.giveMana(d.player1, Color.GREEN, 2)
        assertEquals(0, d.counters(d.cast(d.player1, "Grizzly Bears")))
    }

    @Test
    fun devoidCreaturesAreColorless() {
        val d = customScenario(TwinsOfDiscord, *lib)
        d.putCreatureOnBattlefield(d.player1, twins)
        d.bolt(d.player1, d.player2)
        d.giveMana(d.player1, Color.GREEN)
        assertEquals(2, d.counters(d.cast(d.player1, "Blisterpod")))
    }

    @Test
    fun combatDamageToTheOpponentEnablesIt() {
        val d = customScenario(TwinsOfDiscord, *lib)
        d.putCreatureOnBattlefield(d.player1, twins)
        val bears = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears").also { d.removeSummoningSickness(it) }
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(d.player1, listOf(bears), d.player2).error)
        d.resolveChoosing("Odd")
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertEquals(18, d.getLifeTotal(d.player2))
        assertEquals(2, d.counters(d.cast(d.player1, "Ornithopter")))
    }

    @Test
    fun anOpponentsCreatureGetsNothing() {
        val d = customScenario(TwinsOfDiscord, *lib)
        d.putCreatureOnBattlefield(d.player1, twins)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        // Both players damaged, so either reading of "an opponent" would be satisfied.
        d.bolt(d.player2, d.player1)
        d.bolt(d.player2, d.player2, spell = "Shock")
        assertEquals(0, d.counters(d.cast(d.player2, "Ornithopter")))
    }

    @Test
    fun twoTwinsGiveFourCounters() {
        val d = customScenario(TwinsOfDiscord, *lib)
        d.putCreatureOnBattlefield(d.player1, twins)
        d.putCreatureOnBattlefield(d.player1, twins)
        d.bolt(d.player1, d.player2)
        assertEquals(4, d.counters(d.cast(d.player1, "Ornithopter")))
    }

    @Test
    fun theGrantEndsWhenTheTwinsLeaves() {
        val d = customScenario(TwinsOfDiscord, *lib)
        val t = d.putCreatureOnBattlefield(d.player1, twins)
        d.moveToGraveyard(t)
        d.bolt(d.player1, d.player2)
        assertEquals(0, d.counters(d.cast(d.player1, "Ornithopter")))
    }

    @Test
    fun theTwinsDoesNotGrantItselfBloodthirst() {
        val d = customScenario(TwinsOfDiscord, *lib)
        d.bolt(d.player1, d.player2)
        d.giveColorlessMana(d.player1, 7)
        val t = d.cast(d.player1, twins)
        assertEquals(0, d.counters(t))
        assertEquals(8, d.projected.getPower(t))
    }

    @Test
    fun aSecondTwinsGetsTheFirstOnesGrant() {
        val d = customScenario(TwinsOfDiscord, *lib)
        val first = d.putCreatureOnBattlefield(d.player1, twins)
        d.bolt(d.player1, d.player2)
        d.giveColorlessMana(d.player1, 7)
        val second = d.cast(d.player1, twins)
        assertEquals(2, d.counters(second))
        assertEquals(0, d.counters(first))
    }
}
