package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MutatedCultist
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

/**
 * Mutated Cultist (M3C #53), Oracle: "Devoid / When you cast this spell, remove all counters from up
 * to one target permanent or opponent. The next spell you cast this turn costs {1} less to cast for
 * each counter removed this way. / Deathtouch"
 *
 * Cast trigger (resolves above the Cultist; a direct put onto the battlefield doesn't trigger it);
 * up to one target, a permanent or an opponent, never yourself; every counter kind, on a permanent
 * or a player; the discount is generic-only, floored at {0}, spent by the next spell cast after the
 * trigger resolves even if only partly used, and gone at the next turn.
 */
class MutatedCultistScenarioTest {

    private val cultist = "Mutated Cultist"

    private fun GameTestDriver.counters(id: EntityId, type: CounterType): Int =
        state.getEntity(id)!!.get<CountersComponent>()?.getCount(type) ?: 0

    private fun GameTestDriver.myDiscounts() = state.spellCostModifiers.filter { it.controllerId == player1 }

    /** Casts the Cultist for {2}{B} and answers its cast trigger's target prompt with [targets]. Returns its id. */
    private fun GameTestDriver.castCultist(targets: List<EntityId>): EntityId {
        val c = putCardInHand(player1, cultist)
        giveMana(player1, Color.BLACK); giveColorlessMana(player1, 2)
        assertNull(castSpell(player1, c).error)
        val decision = state.pendingDecision
        assertTrue("cast trigger asks for its target", decision is ChooseTargetsDecision)
        decide(Choices(targets = targets))
        return c
    }

    @Test
    fun devoidDeathtouchOneThree() {
        val d = customScenario(MutatedCultist)
        val c = d.putCreatureOnBattlefield(d.player1, cultist)
        assertTrue("devoid: the card has no color", d.cardRegistry.getCard(cultist)!!.colors.isEmpty())
        assertTrue(d.projected.getColors(c).isEmpty())
        assertTrue(d.projected.hasKeyword(c, Keyword.DEATHTOUCH))
        assertEquals(1, d.projected.getPower(c))
        assertEquals(3, d.projected.getToughness(c))
    }

    @Test
    fun targetsAnyPermanentOrAnOpponentButNotYouAndMayTargetNothing() {
        val d = customScenario(MutatedCultist, "Grizzly Bears")
        val theirs = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        val mine = d.putCreatureOnBattlefield(d.player1, "Grizzly Bears")
        val c = d.putCardInHand(d.player1, cultist)
        d.giveMana(d.player1, Color.BLACK); d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, c).error)
        val decision = d.state.pendingDecision as ChooseTargetsDecision
        val req = decision.targetRequirements.single()
        assertEquals("up to one", 0, req.minTargets)
        assertEquals(1, req.maxTargets)
        val legal = decision.legalTargets[req.index].orEmpty()
        assertTrue("opponent", d.player2 in legal)
        assertFalse("not yourself", d.player1 in legal)
        assertTrue("their permanent", theirs in legal)
        assertTrue("your permanent", mine in legal)
    }

    @Test
    fun castTriggerResolvesFirstClearsEveryKindAndDiscountsTheNextSpell() {
        val d = customScenario(MutatedCultist, "Grizzly Bears", "Hill Giant")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.addComponent(bear, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2, CounterType.CHARGE to 1)))
        val c = d.castCultist(listOf(bear))

        d.bothPass() // the trigger, above the Cultist
        assertEquals(0, d.counters(bear, CounterType.PLUS_ONE_PLUS_ONE))
        assertEquals(0, d.counters(bear, CounterType.CHARGE))
        assertTrue("Cultist still on the stack", c in d.state.stack)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(d.player1, cultist).size)

        val giant = d.putCardInHand(d.player1, "Hill Giant")
        d.giveMana(d.player1, Color.RED)
        assertNull("three counters: {3}{R} costs {R}", d.castSpell(d.player1, giant).error)
        d.resolveAll()
        assertEquals(1, d.permanentsNamed(d.player1, "Hill Giant").size)
    }

    @Test
    fun removesAllOfAnOpponentsCountersOnlyAndDiscountNeverTouchesColouredPips() {
        val d = customScenario(MutatedCultist, "Craw Wurm")
        d.addComponent(d.player2, CountersComponent(mapOf(
            CounterType.POISON to 3, CounterType.ENERGY to 2, CounterType.EXPERIENCE to 1,
        )))
        d.addComponent(d.player1, CountersComponent(mapOf(CounterType.POISON to 2)))
        d.castCultist(listOf(d.player2))
        d.resolveAll()
        assertEquals(0, d.counters(d.player2, CounterType.POISON))
        assertEquals(0, d.counters(d.player2, CounterType.ENERGY))
        assertEquals(0, d.counters(d.player2, CounterType.EXPERIENCE))
        assertEquals("your own counters untouched", 2, d.counters(d.player1, CounterType.POISON))
        assertEquals(6, d.myDiscounts().single().amount)

        // {4}{G}{G} less 6: generic floors at {0}, the two {G} stay.
        val wurm = d.putCardInHand(d.player1, "Craw Wurm")
        d.giveMana(d.player1, Color.GREEN)
        assertNotNull("coloured pips can't be reduced", d.castSpell(d.player1, wurm).error)
        d.giveMana(d.player1, Color.GREEN); d.giveColorlessMana(d.player1, 1)
        assertNull(d.castSpell(d.player1, wurm).error)
        assertEquals("no negative cost: spare mana stays", 1, d.manaPool(d.player1).colorless)
        assertTrue("spent", d.myDiscounts().isEmpty())
    }

    @Test
    fun noTargetOrNoCountersMeansNoDiscount() {
        val d = customScenario(MutatedCultist, "Grizzly Bears", "Hill Giant")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.addComponent(bear, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)))
        d.addComponent(d.player2, CountersComponent(mapOf(CounterType.POISON to 1)))
        d.castCultist(emptyList())
        d.resolveAll()
        assertEquals("zero targets: nothing removed", 2, d.counters(bear, CounterType.PLUS_ONE_PLUS_ONE))
        assertEquals(1, d.counters(d.player2, CounterType.POISON))
        assertTrue(d.myDiscounts().isEmpty())

        val bare = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.castCultist(listOf(bare))
        d.resolveAll()
        assertTrue("a permanent without counters gives no discount", d.myDiscounts().isEmpty())
        val giant = d.putCardInHand(d.player1, "Hill Giant")
        d.giveMana(d.player1, Color.RED)
        assertNotNull(d.castSpell(d.player1, giant).error)
    }

    @Test
    fun discountIsSpentByTheNextSpellEvenIfPartlyUsed() {
        val d = customScenario(MutatedCultist, "Grizzly Bears", "Hill Giant")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.addComponent(bear, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 3)))
        d.castCultist(listOf(bear))
        d.resolveAll()

        val first = d.putCardInHand(d.player1, "Grizzly Bears")
        d.giveMana(d.player1, Color.GREEN)
        assertNull("{1}{G} uses one of the three", d.castSpell(d.player1, first).error)
        d.resolveAll()
        assertTrue(d.myDiscounts().isEmpty())
        val second = d.putCardInHand(d.player1, "Hill Giant")
        d.giveMana(d.player1, Color.RED); d.giveColorlessMana(d.player1, 2)
        assertNotNull("the rest is gone: {3}{R} at full price", d.castSpell(d.player1, second).error)
    }

    @Test
    fun aSpellCastInResponseIsNotTheNextSpell() {
        val d = customScenario(MutatedCultist, "Grizzly Bears", "Lightning Strike", "Hill Giant")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.addComponent(bear, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 3)))
        d.castCultist(listOf(bear))
        val strike = d.putCardInHand(d.player1, "Lightning Strike")
        d.giveMana(d.player1, Color.RED)
        assertNotNull("trigger hasn't resolved: no discount yet", d.castSpell(d.player1, strike, listOf(d.player2)).error)
        d.resolveAll()
        assertEquals(3, d.myDiscounts().single().amount)
        val giant = d.putCardInHand(d.player1, "Hill Giant")
        assertNull(d.castSpell(d.player1, giant).error) // the {R} still in the pool
    }

    @Test
    fun unusedDiscountEndsWithTheTurn() {
        val d = customScenario(MutatedCultist, "Grizzly Bears", "Lightning Strike")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.addComponent(bear, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)))
        d.castCultist(listOf(bear))
        d.resolveAll()
        assertEquals(2, d.myDiscounts().single().amount)
        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        assertTrue(d.myDiscounts().isEmpty())
        val strike = d.putCardInHand(d.player1, "Lightning Strike")
        d.giveMana(d.player1, Color.RED)
        assertNotNull("{1}{R} at full price next turn", d.castSpell(d.player1, strike, listOf(d.player2)).error)
    }

    @Test
    fun puttingItOntoTheBattlefieldDoesNotTrigger() {
        val d = customScenario(MutatedCultist, "Grizzly Bears")
        val bear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.addComponent(bear, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 2)))
        d.putCreatureOnBattlefield(d.player1, cultist)
        d.resolveAll()
        assertNull(d.state.pendingDecision)
        assertEquals(2, d.counters(bear, CounterType.PLUS_ONE_PLUS_ONE))
        assertTrue(d.myDiscounts().isEmpty())
    }
}
