package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MoltenSlagheap
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class MoltenSlagheapIndependentCertificationTest {
    private fun GameTestDriver.playSlagheap(player: EntityId): EntityId {
        val slagheap = putCardInHand(player, "Molten Slagheap")
        assertNull(playLand(player, slagheap).error)
        resolveAll()
        return slagheap
    }

    private fun GameTestDriver.storageCounters(slagheap: EntityId): Int =
        state.getEntity(slagheap)?.get<CountersComponent>()?.getCount(CounterType.STORAGE) ?: 0

    private fun GameTestDriver.addStorageCounters(player: EntityId, slagheap: EntityId, count: Int) {
        repeat(count) {
            giveColorlessMana(player, 1)
            assertNull(activate(player, slagheap, index = 1).error)
            resolveAll()
            untapPermanent(slagheap)
        }
    }

    @Test
    fun tappingTheLandAddsOneColorlessAndCannotBeRepeatedWhileTapped() {
        val d = customScenario(MoltenSlagheap)
        val p = d.player1
        val slagheap = d.playSlagheap(p)

        assertEquals(0, d.manaPool(p).colorless)
        assertNull(d.activate(p, slagheap, index = 0).error)
        d.resolveAll()

        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.isTapped(slagheap))
        assertNotNull(d.activate(p, slagheap, index = 0).error)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun storageAbilityPaysOneAndTapsToAddOneStorageCounter() {
        val d = customScenario(MoltenSlagheap)
        val p = d.player1
        val slagheap = d.playSlagheap(p)
        d.giveColorlessMana(p, 1)

        assertNull(d.activate(p, slagheap, index = 1).error)
        d.resolveAll()

        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(1, d.storageCounters(slagheap))
        assertTrue(d.isTapped(slagheap))

        d.giveColorlessMana(p, 1)
        assertNotNull(d.activate(p, slagheap, index = 1).error)
        assertEquals(1, d.manaPool(p).colorless)
        assertEquals(1, d.storageCounters(slagheap))
    }

    @Test
    fun storageAbilityCannotBeActivatedWithoutItsGenericManaCost() {
        val d = customScenario(MoltenSlagheap)
        val p = d.player1
        val slagheap = d.playSlagheap(p)

        assertNotNull(d.activate(p, slagheap, index = 1).error)

        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(0, d.storageCounters(slagheap))
        assertFalse(d.isTapped(slagheap))
    }

    @Test
    fun removingTwoStorageCountersAddsOneBlackAndOneRedWithoutTapping() {
        val d = customScenario(MoltenSlagheap)
        val p = d.player1
        val slagheap = d.playSlagheap(p)
        d.addStorageCounters(p, slagheap, 2)
        assertEquals(2, d.storageCounters(slagheap))
        d.giveColorlessMana(p, 1)

        assertNull(d.activate(p, slagheap, index = 2).error)
        var choicesMade = 0
        var stepsRemaining = 60
        while (d.state.pendingDecision != null || d.state.stack.isNotEmpty()) {
            assertTrue("mana ability did not finish resolving", stepsRemaining-- > 0)
            if (d.state.pendingDecision != null) {
                assertTrue("unexpected extra mana color choice", choicesMade < 2)
                val color = if (choicesMade++ == 0) Color.BLACK else Color.RED
                d.decide(Choices(color = color))
            } else {
                d.bothPass()
            }
        }

        assertEquals(2, choicesMade)
        assertEquals(0, d.storageCounters(slagheap))
        assertEquals(1, d.manaPool(p).black)
        assertEquals(1, d.manaPool(p).red)
        assertEquals(0, d.manaPool(p).colorless)
        assertFalse(d.isTapped(slagheap))
    }

    @Test
    fun storageCountersCannotBeConvertedWithoutPayingTheGenericManaCost() {
        val d = customScenario(MoltenSlagheap)
        val p = d.player1
        val slagheap = d.playSlagheap(p)
        d.addStorageCounters(p, slagheap, 2)

        assertNotNull(d.activate(p, slagheap, index = 2).error)

        assertEquals(2, d.storageCounters(slagheap))
        assertEquals(0, d.manaPool(p).black)
        assertEquals(0, d.manaPool(p).red)
        assertFalse(d.isTapped(slagheap))
    }
}
