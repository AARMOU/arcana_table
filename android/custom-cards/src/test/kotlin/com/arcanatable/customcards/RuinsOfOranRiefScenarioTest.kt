package com.arcanatable.customcards

import com.arcanatable.customcards.cards.RuinsOfOranRief
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class RuinsOfOranRiefScenarioTest {
    @Test
    fun entersTappedAndAddsCounterToColorlessCreatureWhichEnteredThisTurn() {
        val d = customScenario(RuinsOfOranRief, "Ulamog's Crusher")
        val p = d.player1
        val q = d.player2
        val card = d.putCardInHand(p, "Ruins of Oran-Rief")
        assertNull(d.playLand(p, card).error)
        d.resolveAll()
        val ruins = d.findPermanent(p, "Ruins of Oran-Rief")!!
        assertTrue(d.isTapped(ruins))
        d.passPriorityUntil(Step.UPKEEP, activePlayer = q)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)
        val eldrazi = d.putCardInHand(p, "Ulamog's Crusher")
        d.giveColorlessMana(p, 8)
        assertNull(d.castSpell(p, eldrazi).error)
        d.resolveAll()
        val creature = d.findPermanent(p, "Ulamog's Crusher")!!
        val id = d.cardRegistry.getCard("Ruins of Oran-Rief")!!.script.activatedAbilities[1].id
        assertNull(d.submit(ActivateAbility(p, ruins, id, targets = listOf(ChosenTarget.Permanent(creature)))).error)
        d.resolveAll()
        assertEquals(1, d.state.getEntity(creature)!!.get<CountersComponent>()!!.getCount(CounterType.PLUS_ONE_PLUS_ONE))
    }

    private fun counter(d: GameTestDriver, ruins: EntityId, target: EntityId) = d.submit(
        ActivateAbility(d.player1, ruins,
            d.cardRegistry.getCard("Ruins of Oran-Rief")!!.script.activatedAbilities[1].id,
            targets = listOf(ChosenTarget.Permanent(target))))

    private fun counters(d: GameTestDriver, target: EntityId) =
        d.state.getEntity(target)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    private fun crusher(d: GameTestDriver, player: EntityId): EntityId {
        val spell = d.putCardInHand(player, "Ulamog's Crusher")
        d.giveColorlessMana(player, 8)
        assertNull(d.castSpell(player, spell).error)
        d.resolveAll()
        return d.findPermanent(player, "Ulamog's Crusher")!!
    }

    @Test fun tappedEntryPreventsEitherModeAndUntappedManaActuallyPaysForSpell() {
        val d = customScenario(RuinsOfOranRief, "Ulamog's Crusher", "Sol Ring")
        val creature = crusher(d, d.player1)
        assertNull(d.playLand(d.player1, d.putCardInHand(d.player1, "Ruins of Oran-Rief")).error)
        d.resolveAll()
        val ruins = d.findPermanent(d.player1, "Ruins of Oran-Rief")!!
        assertNotNull(d.activate(d.player1, ruins).error)
        assertNotNull(counter(d, ruins, creature).error)
        assertEquals(0, d.manaPool(d.player1).colorless)
        assertEquals(0, counters(d, creature))
        d.passPriorityUntil(Step.UPKEEP, activePlayer = d.player2)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player1)
        assertNull(d.activate(d.player1, ruins).error)
        assertTrue(d.state.stack.isEmpty())
        assertEquals(1, d.manaPool(d.player1).colorless)
        assertNotNull(d.activate(d.player1, ruins).error)
        val ring = d.putCardInHand(d.player1, "Sol Ring")
        assertNull(d.castSpell(d.player1, ring).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(d.player1, "Sol Ring"))
        assertEquals(0, d.manaPool(d.player1).colorless)
    }

    @Test fun rejectsColoredCreatureColorlessNoncreatureAndCreatureFromEarlierTurnWithoutTapping() {
        val d = customScenario(RuinsOfOranRief, "Grizzly Bears", "Sol Ring", "Ulamog's Crusher")
        val ruins = d.putPermanentOnBattlefield(d.player1, "Ruins of Oran-Rief")
        val old = d.putCreatureOnBattlefield(d.player1, "Ulamog's Crusher")
        d.giveMana(d.player1, Color.GREEN)
        d.giveColorlessMana(d.player1, 2)
        assertNull(d.castSpell(d.player1, d.putCardInHand(d.player1, "Grizzly Bears")).error)
        d.resolveAll()
        assertNull(d.castSpell(d.player1, d.putCardInHand(d.player1, "Sol Ring")).error)
        d.resolveAll()
        val bears = d.findPermanent(d.player1, "Grizzly Bears")!!
        val ring = d.findPermanent(d.player1, "Sol Ring")!!
        for (target in listOf(bears, ring, old)) {
            assertNotNull("illegal target $target", counter(d, ruins, target).error)
            assertFalse(d.isTapped(ruins))
            assertEquals(0, counters(d, target))
        }
    }

    @Test fun mayTargetAnOpponentsColorlessCreatureThatEnteredThisTurn() {
        val d = customScenario(RuinsOfOranRief, "Ulamog's Crusher")
        val ruins = d.putPermanentOnBattlefield(d.player1, "Ruins of Oran-Rief")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        val creature = crusher(d, d.player2)
        assertNull(d.passPriority(d.player2).error)
        assertNull(counter(d, ruins, creature).error)
        d.resolveAll()
        assertEquals(1, counters(d, creature))
        assertTrue(d.isTapped(ruins))
    }

    @Test fun anActualCastStopsBeingEligibleOnTheNextTurn() {
        val d = customScenario(RuinsOfOranRief, "Ulamog's Crusher")
        val ruins = d.putPermanentOnBattlefield(d.player1, "Ruins of Oran-Rief")
        val creature = crusher(d, d.player1)
        assertNull(counter(d, ruins, creature).error)
        d.resolveAll()
        assertEquals(1, counters(d, creature))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player1)
        assertNotNull(counter(d, ruins, creature).error)
        assertFalse(d.isTapped(ruins))
        assertEquals(1, counters(d, creature))
    }
}
