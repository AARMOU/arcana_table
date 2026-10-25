package com.arcanatable.customcards
import com.arcanatable.customcards.cards.TendoIceBridge
import com.arcanatable.customcards.support.*
import com.wingedsheep.sdk.core.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import org.junit.Assert.*
import org.junit.Test
class TendoIceBridgeScenarioTest {
    private fun setup(): Pair<GameTestDriver, com.wingedsheep.sdk.model.EntityId> {
        val d = customScenario(TendoIceBridge, "Sol Ring", "Grizzly Bears")
        val p = d.player1
        val land = d.putCardInHand(p, "Tendo Ice Bridge")
        assertNull(d.playLand(p, land).error)
        d.resolveAll()
        return d to d.findPermanent(p, "Tendo Ice Bridge")!!
    }
    @Test fun chargeCounterPaysForOneColoredManaUse() {
        val d = customScenario(TendoIceBridge)
        val p = d.player1
        val land = d.putCardInHand(p, "Tendo Ice Bridge")
        assertNull(d.playLand(p, land).error)
        d.resolveAll()
        val bridge = d.findPermanent(p, "Tendo Ice Bridge")!!
        assertEquals(1, d.state.getEntity(bridge)!!.get<CountersComponent>()!!.getCount(CounterType.CHARGE))
        assertNull(d.activate(p, bridge, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))
        assertEquals(0, d.state.getEntity(bridge)!!.get<CountersComponent>()?.getCount(CounterType.CHARGE) ?: 0)
        assertEquals(1, d.manaPool(p).green)
    }

    @Test fun spentChargeCannotBeUsedAgainAfterUntappingButColorlessModeStillWorks() {
        val (d, land) = setup()
        val p = d.player1
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll(Choices(color = Color.GREEN))
        d.untapPermanent(land)
        val before = d.manaPool(p)
        assertNotNull("Untapping does not restore the spent charge", d.activate(p, land, index = 1).error)
        assertEquals(before, d.manaPool(p))
        assertFalse("Failed payment must not tap the land", d.isTapped(land))
        assertNull(d.activate(p, land, index = 0).error)
        assertEquals(1, d.manaPool(p).colorless)
        assertEquals(1, d.manaPool(p).green)
        val bear = d.putCardInHand(p, "Grizzly Bears")
        assertNull("Both modes produce spendable mana", d.castSpell(p, bear).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Grizzly Bears"))
        assertEquals(0, d.manaPool(p).colorless)
        assertEquals(0, d.manaPool(p).green)
    }

    @Test fun colorlessModeDoesNotConsumeTheChargeAndPaysForAnArtifact() {
        val (d, land) = setup()
        val p = d.player1
        assertNull(d.activate(p, land, index = 0).error)
        assertEquals(1, d.state.getEntity(land)!!.get<CountersComponent>()!!.getCount(CounterType.CHARGE))
        val ring = d.putCardInHand(p, "Sol Ring")
        assertNull(d.castSpell(p, ring).error)
        d.resolveAll()
        assertNotNull(d.findPermanent(p, "Sol Ring"))
        d.untapPermanent(land)
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll(Choices(color = Color.BLUE))
        assertEquals(1, d.manaPool(p).blue)
        assertEquals(0, d.state.getEntity(land)!!.get<CountersComponent>()?.getCount(CounterType.CHARGE) ?: 0)
    }

    @Test fun tappingForColoredManaPreventsASecondActivationOfEitherMode() {
        val (d, land) = setup()
        val p = d.player1
        assertNull(d.activate(p, land, index = 1).error)
        d.resolveAll(Choices(color = Color.RED))
        val before = d.manaPool(p)
        assertNotNull(d.activate(p, land, index = 0).error)
        assertNotNull(d.activate(p, land, index = 1).error)
        assertEquals(before, d.manaPool(p))
        assertEquals(1, d.manaPool(p).red)
    }
}
