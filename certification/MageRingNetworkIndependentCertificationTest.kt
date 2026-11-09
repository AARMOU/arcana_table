package com.arcanatable.customcards

import com.arcanatable.customcards.cards.MageRingNetwork
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class MageRingNetworkIndependentCertificationTest {
    private fun GameTestDriver.storageCounters(land: EntityId): Int =
        state.getEntity(land)?.get<CountersComponent>()?.getCount(CounterType.STORAGE) ?: 0

    @Test
    fun tapAbilityAddsOneColorlessAndCannotBeActivatedAgainWhileTapped() {
        val d = customScenario(MageRingNetwork)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mage-Ring Network")

        assertNull(d.activate(p, land, index = 0).error)
        d.resolveAll()

        assertEquals(1, d.manaPool(p).colorless)
        assertTrue(d.isTapped(land))
        assertNotNull(d.activate(p, land, index = 0).error)
        assertEquals(1, d.manaPool(p).colorless)
    }

    @Test
    fun storageAbilityPaysOneManaToPutOneCounterAndRequiresAnUntappedLand() {
        val d = customScenario(MageRingNetwork)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mage-Ring Network")

        assertNotNull(d.activate(p, land, index = 1).error)
        assertEquals(0, d.storageCounters(land))
        assertFalse(d.isTapped(land))

        d.giveColorlessMana(p, 1)
        assertNull(d.activate(p, land, index = 1).error)
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.isTapped(land))
        d.resolveAll()

        assertEquals(1, d.storageCounters(land))
        assertNotNull(d.activate(p, land, index = 1).error)
        assertEquals(1, d.storageCounters(land))
    }

    @Test
    fun storageCountersAreConvertedToAnEqualAmountOfColorlessMana() {
        val d = customScenario(MageRingNetwork)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mage-Ring Network")

        repeat(2) { index ->
            d.giveColorlessMana(p, 1)
            assertNull(d.activate(p, land, index = 1).error)
            d.resolveAll()
            assertEquals(index + 1, d.storageCounters(land))
            d.untapPermanent(land)
        }

        val manaAbility = d.cardRegistry.getCard("Mage-Ring Network")!!.script.activatedAbilities[2].id
        assertNull(d.submit(ActivateAbility(p, land, manaAbility, xValue = 2)).error)
        d.resolveAll()

        assertEquals(0, d.storageCounters(land))
        assertEquals(2, d.manaPool(p).colorless)
        assertTrue(d.isTapped(land))
    }

    @Test
    fun counterRemovalAbilityMayBeActivatedWithNoStorageCounters() {
        val d = customScenario(MageRingNetwork)
        val p = d.player1
        val land = d.putPermanentOnBattlefield(p, "Mage-Ring Network")

        assertNull(d.activate(p, land, index = 2).error)
        d.resolveAll()

        assertEquals(0, d.storageCounters(land))
        assertEquals(0, d.manaPool(p).colorless)
        assertTrue(d.isTapped(land))
    }

    @Test
    fun onlyTheControllerCanActivateTheLand() {
        val d = customScenario(MageRingNetwork)
        val p = d.player1
        val opponent = d.player2
        val land = d.putPermanentOnBattlefield(p, "Mage-Ring Network")
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)

        assertNotNull(d.activate(opponent, land, index = 0).error)
        assertFalse(d.isTapped(land))
        assertEquals(0, d.manaPool(opponent).colorless)
    }
}
