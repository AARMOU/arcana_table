package com.arcanatable.customcards

import com.arcanatable.customcards.cards.TriarchStalker
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.chosenOpponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.tooling.CardLoader
import org.junit.Assert.*
import org.junit.Test

/**
 * Triarch Stalker (40K #67): Targeting Relay — at the beginning of combat on your turn, choose an
 * opponent; creatures attacking the last chosen player have menace. Modelling notes in [TriarchStalker].
 */
class TriarchStalkerScenarioTest {

    private val library = arrayOf("Hill Giant", "Grizzly Bears", "Jace Beleren", "Hero of Bladehold", "Unsummon")

    private fun duel() = customScenario(TriarchStalker, *library)

    /** A three-player free-for-all (turn order p1, p2, p3) in p1's first precombat main phase. */
    private fun table(): Pair<GameTestDriver, List<EntityId>> {
        val d = CardPool.driver(*library).apply {
            registerCards(listOf(CardLoader.fromJsonPreservingIds(exportLine(TriarchStalker))))
        }
        val seats = d.initMultiplayer(List(3) { Deck.of("Plains" to 40) })
        d.passPriorityUntil(Step.PRECOMBAT_MAIN)
        return d to seats
    }

    private fun GameTestDriver.chosen(stalker: EntityId): EntityId? = state.getEntity(stalker)?.chosenOpponent()

    private fun GameTestDriver.menace(id: EntityId) = projected.hasKeyword(id, Keyword.MENACE)

    private fun GameTestDriver.ready(player: EntityId, name: String) =
        putCreatureOnBattlefield(player, name).also { removeSummoningSickness(it) }

    /** Resolves the stack with every player passing, picking [choose] at an opponent choice. */
    private fun GameTestDriver.settle(choose: EntityId? = null) {
        repeat(100) {
            val decision = state.pendingDecision
            when {
                decision is ChooseOptionDecision && choose != null -> {
                    val name = state.getEntity(choose)!!.get<PlayerComponent>()!!.name
                    val index = decision.options.indexOf(name)
                    assertTrue("$name is offered: ${decision.options}", index >= 0)
                    assertNull(submitDecision(decision.playerId, OptionChosenResponse(decision.id, index)).error)
                }
                decision != null -> decide()
                state.stack.isNotEmpty() -> passPriority(state.priorityPlayerId!!)
                else -> return
            }
        }
        error("Stack did not settle: ${getStackSpellNames()}")
    }

    @Test
    fun triggerFiresOnlyAtTheBeginningOfCombatOnYourTurn() {
        val (d, seats) = table()
        val (p1, p2, p3) = seats
        val stalker = d.putCreatureOnBattlefield(p1, "Triarch Stalker")
        assertNull("nothing chosen before the trigger", d.chosen(stalker))
        assertTrue("no trigger in the main phase", d.state.stack.isEmpty())

        d.passPriorityUntil(Step.BEGIN_COMBAT)
        while (d.state.pendingDecision == null && d.state.stack.isNotEmpty()) d.passPriority(d.state.priorityPlayerId!!)
        val decision = d.state.pendingDecision as ChooseOptionDecision
        assertEquals("the Stalker's trigger asks", stalker, decision.context.sourceId)
        assertEquals("p1 chooses between its two opponents", p1, decision.playerId)
        assertEquals(2, decision.options.size)
        d.settle(choose = p3)
        assertEquals(p3, d.chosen(stalker))

        d.passPriorityUntil(Step.BEGIN_COMBAT, p2)
        assertTrue("no trigger at the beginning of an opponent's combat", d.state.stack.isEmpty())
        assertNull(d.state.pendingDecision)
        d.passPriorityUntil(Step.BEGIN_COMBAT, p3)
        assertTrue(d.state.stack.isEmpty())
        assertEquals("the choice is unchanged", p3, d.chosen(stalker))
    }

    @Test
    fun attackersOfTheChosenPlayerCantBeBlockedByJustOneCreature() {
        val d = duel()
        val p = d.player1
        val o = d.player2
        val stalker = d.putCreatureOnBattlefield(p, "Triarch Stalker")
        val giant = d.ready(p, "Hill Giant")
        val (b1, b2) = List(2) { d.putCreatureOnBattlefield(o, "Grizzly Bears") }
        assertFalse("not attacking yet", d.menace(giant))

        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.resolveAll()
        assertEquals("forced choice with one opponent", o, d.chosen(stalker))
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(giant), o).error)
        assertTrue(d.menace(giant))
        assertFalse("not attacking", d.menace(stalker))
        assertFalse("the defender's creatures", d.menace(b1))

        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull("one blocker isn't enough", d.declareBlockers(o, mapOf(b1 to listOf(giant))).error)
        assertNull("two blockers are", d.declareBlockers(o, mapOf(b1 to listOf(giant), b2 to listOf(giant))).error)
    }

    @Test
    fun attackersOfADifferentOpponentDontHaveMenace() {
        val (d, seats) = table()
        val (p1, p2, p3) = seats
        d.putCreatureOnBattlefield(p1, "Triarch Stalker")
        val atChosen = d.ready(p1, "Hill Giant")
        val atOther = d.ready(p1, "Hill Giant")
        val (c1, c2) = List(2) { d.putCreatureOnBattlefield(p2, "Grizzly Bears") }
        val lone = d.putCreatureOnBattlefield(p3, "Grizzly Bears")

        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.settle(choose = p2)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p1, mapOf(atChosen to p2, atOther to p3)).error)
        assertTrue(d.menace(atChosen))
        assertFalse("attacking the other opponent", d.menace(atOther))

        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(p2, mapOf(c1 to listOf(atChosen))).error)
        assertNull(d.declareBlockers(p2, mapOf(c1 to listOf(atChosen), c2 to listOf(atChosen))).error)
        assertNull("one creature blocks the other attacker", d.declareBlockers(p3, mapOf(lone to listOf(atOther))).error)
    }

    @Test
    fun anAttackerOfTheChosenPlayersPlaneswalkerDoesntHaveMenace() {
        val d = duel()
        val p = d.player1
        val o = d.player2
        d.putCreatureOnBattlefield(p, "Triarch Stalker")
        val jace = d.putPermanentOnBattlefield(o, "Jace Beleren")
        d.addComponent(jace, CountersComponent(mapOf(CounterType.LOYALTY to 3)))
        val atWalker = d.ready(p, "Hill Giant")
        val atPlayer = d.ready(p, "Hill Giant")
        val bear = d.putCreatureOnBattlefield(o, "Grizzly Bears")

        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, mapOf(atWalker to jace, atPlayer to o)).error)
        assertFalse("attacking a planeswalker, not the player", d.menace(atWalker))
        assertTrue(d.menace(atPlayer))

        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(o, mapOf(bear to listOf(atPlayer))).error)
        assertNull("one creature blocks the planeswalker's attacker", d.declareBlockers(o, mapOf(bear to listOf(atWalker))).error)
    }

    @Test
    fun creaturesPutOntoTheBattlefieldAttackingTheChosenPlayerHaveMenace() {
        val d = duel()
        val p = d.player1
        val o = d.player2
        d.putCreatureOnBattlefield(p, "Triarch Stalker")
        val hero = d.ready(p, "Hero of Bladehold")
        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        val before = d.getCreatures(p).toSet()
        assertNull(d.declareAttackers(p, listOf(hero), o).error)
        d.resolveAll()

        val tokens = d.getCreatures(p).filterNot { it in before }
        assertEquals(2, tokens.size)
        tokens.forEach { assertTrue("token entered attacking the chosen player", d.menace(it)) }
        assertTrue(d.menace(hero))
    }

    @Test
    fun choosingAgainOnYourNextTurnSwitchesThePlayer() {
        val (d, seats) = table()
        val (p1, p2, p3) = seats
        val stalker = d.putCreatureOnBattlefield(p1, "Triarch Stalker")
        val a = d.ready(p1, "Hill Giant")
        val b = d.ready(p1, "Hill Giant")
        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.settle(choose = p2)
        assertEquals(p2, d.chosen(stalker))

        d.passPriorityUntil(Step.UPKEEP, p2)
        d.passPriorityUntil(Step.BEGIN_COMBAT, p1)
        assertEquals("the old choice holds until the trigger resolves", p2, d.chosen(stalker))
        d.settle(choose = p3)
        assertEquals(p3, d.chosen(stalker))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p1, mapOf(a to p2, b to p3)).error)
        assertFalse("the previous player is no longer chosen", d.menace(a))
        assertTrue(d.menace(b))
    }

    @Test
    fun theChoicePersistsThroughAnotherOpponentsTurn() {
        val (d, seats) = table()
        val (p1, p2, p3) = seats
        d.putCreatureOnBattlefield(p1, "Triarch Stalker")
        val (c1, c2) = List(2) { d.putCreatureOnBattlefield(p2, "Grizzly Bears") }
        val raider = d.ready(p3, "Hill Giant")
        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.settle(choose = p2)

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, p3)
        assertNull(d.declareAttackers(p3, listOf(raider), p2).error)
        assertTrue("another opponent's creature attacking the chosen player", d.menace(raider))

        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(p2, mapOf(c1 to listOf(raider))).error)
        assertNull(d.declareBlockers(p2, mapOf(c1 to listOf(raider), c2 to listOf(raider))).error)
    }

    @Test
    fun noMenaceOnceTheStalkerLeavesAndAReturnedStalkerHasNoChosenPlayer() {
        val (d, seats) = table()
        val (p1, p2, p3) = seats
        val stalker = d.putCreatureOnBattlefield(p1, "Triarch Stalker")
        val giant = d.ready(p1, "Hill Giant")
        val bear = d.putCreatureOnBattlefield(p2, "Grizzly Bears")
        val raider = d.ready(p3, "Hill Giant")
        val unsummon = d.putCardInHand(p1, "Unsummon")
        d.passPriorityUntil(Step.BEGIN_COMBAT)
        d.settle(choose = p2)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p1, listOf(giant), p2).error)
        assertTrue(d.menace(giant))

        d.giveMana(p1, Color.BLUE)
        assertNull(d.castSpell(p1, unsummon, targets = listOf(stalker)).error)
        d.settle()
        assertNotNull("bounced", d.findCardInHand(p1, "Triarch Stalker"))
        assertFalse("the static ended with the Stalker", d.menace(giant))
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull("one creature can block now", d.declareBlockers(p2, mapOf(bear to listOf(giant))).error)

        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        d.giveColorlessMana(p1, 3)
        d.giveMana(p1, Color.BLACK, 2)
        assertNull(d.castSpell(p1, d.findCardInHand(p1, "Triarch Stalker")!!).error)
        d.settle()
        val returned = d.findPermanent(p1, "Triarch Stalker")!!
        assertNull("a new object has no chosen player", d.chosen(returned))

        d.passPriorityUntil(Step.DECLARE_ATTACKERS, p3)
        assertNull(d.declareAttackers(p3, listOf(raider), p2).error)
        assertFalse("the old choice didn't come back with it", d.menace(raider))
    }
}
