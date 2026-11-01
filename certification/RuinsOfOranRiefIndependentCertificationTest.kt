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

class RuinsOfOranRiefIndependentCertificationTest {
    private fun GameTestDriver.playRuins(player: EntityId): EntityId {
        val ruins = putCardInHand(player, "Ruins of Oran-Rief")
        assertNull(playLand(player, ruins).error)
        resolveAll()
        return ruins
    }

    private fun GameTestDriver.activateCounterAbility(
        player: EntityId,
        ruins: EntityId,
        target: EntityId,
    ) = submit(
        ActivateAbility(
            player,
            ruins,
            cardRegistry.getCard("Ruins of Oran-Rief")!!.script.activatedAbilities[1].id,
            targets = listOf(ChosenTarget.Permanent(target)),
        ),
    )

    private fun GameTestDriver.plusOneCounters(permanent: EntityId): Int =
        state.getEntity(permanent)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    @Test
    fun entersTappedAndItsManaAbilityAddsOneColorlessAfterUntapping() {
        val d = customScenario(RuinsOfOranRief)
        val p = d.player1
        val ruins = d.playRuins(p)

        assertTrue(d.isTapped(ruins))
        assertNotNull(d.activate(p, ruins, index = 0).error)
        assertEquals(0, d.manaPool(p).colorless)

        d.untapPermanent(ruins)
        assertNull(d.activate(p, ruins, index = 0).error)
        d.resolveAll()
        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.isTapped(ruins))

        assertNotNull(d.activate(p, ruins, index = 0).error)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun addsOneCounterToAColorlessCreatureThatEnteredThisTurn() {
        val d = customScenario(RuinsOfOranRief, "Ornithopter")
        val p = d.player1
        val ornithopter = d.putCardInHand(p, "Ornithopter")
        assertNull(d.castSpell(p, ornithopter).error)
        d.resolveAll()
        val ruins = d.playRuins(p)

        assertNotNull(d.activateCounterAbility(p, ruins, ornithopter).error)
        assertTrue(d.isTapped(ruins))
        assertEquals(0, d.plusOneCounters(ornithopter))
        d.untapPermanent(ruins)

        assertEquals(0, d.plusOneCounters(ornithopter))
        assertNull(d.activateCounterAbility(p, ruins, ornithopter).error)
        assertTrue(d.isTapped(ruins))
        d.resolveAll()

        assertEquals(1, d.plusOneCounters(ornithopter))
        assertEquals(1, d.projected.getPower(ornithopter))
        assertEquals(3, d.projected.getToughness(ornithopter))
    }

    @Test
    fun cannotTargetAColoredCreatureEvenWhenItEnteredThisTurn() {
        val d = customScenario(RuinsOfOranRief, "Grizzly Bears")
        val p = d.player1
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)
        assertNull(d.castSpell(p, bear).error)
        d.resolveAll()
        val ruins = d.playRuins(p)
        d.untapPermanent(ruins)

        assertNotNull(d.activateCounterAbility(p, ruins, bear).error)
        assertFalse(d.isTapped(ruins))
        assertEquals(0, d.plusOneCounters(bear))
    }

    @Test
    fun cannotTargetAColorlessCreatureThatEnteredOnAnEarlierTurn() {
        val d = customScenario(RuinsOfOranRief, "Ornithopter")
        val p = d.player1
        val ornithopter = d.putCardInHand(p, "Ornithopter")
        assertNull(d.castSpell(p, ornithopter).error)
        d.resolveAll()
        val ruins = d.playRuins(p)

        d.passPriorityUntil(Step.END)
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = p)

        assertTrue(d.getPermanents(p).contains(ornithopter))
        assertFalse(d.isTapped(ruins))
        assertNotNull(d.activateCounterAbility(p, ruins, ornithopter).error)
        assertFalse(d.isTapped(ruins))
        assertEquals(0, d.plusOneCounters(ornithopter))
    }
}
