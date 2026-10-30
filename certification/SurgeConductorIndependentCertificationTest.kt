package com.arcanatable.customcards

import com.arcanatable.customcards.cards.SurgeConductor
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class SurgeConductorIndependentCertificationTest {
    private fun GameTestDriver.counterCount(permanent: EntityId, type: CounterType): Int =
        state.getEntity(permanent)?.get<CountersComponent>()?.getCount(type) ?: 0

    @Test
    fun anotherControlledNontokenArtifactProliferatesEachSelectedCounterKind() {
        val d = customScenario(SurgeConductor, "Sol Ring", "Grizzly Bears", "Giant Spider")
        val p = d.player1
        val conductor = d.putCreatureOnBattlefield(p, "Surge Conductor")
        val selected = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val unselected = d.putCreatureOnBattlefield(p, "Giant Spider")
        d.addComponent(selected, CountersComponent(mapOf(
            CounterType.PLUS_ONE_PLUS_ONE to 1,
            CounterType.CHARGE to 2,
        )))
        d.addComponent(unselected, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
        val ring = d.putCardInHand(p, "Sol Ring")
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, ring).error)
        assertEquals(1, d.counterCount(selected, CounterType.PLUS_ONE_PLUS_ONE))
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))

        assertTrue(d.getPermanents(p).contains(conductor))
        assertEquals(2, d.counterCount(selected, CounterType.PLUS_ONE_PLUS_ONE))
        assertEquals(3, d.counterCount(selected, CounterType.CHARGE))
        assertEquals(1, d.counterCount(unselected, CounterType.PLUS_ONE_PLUS_ONE))
        assertTrue(d.permanentsNamed(p, "Sol Ring").isNotEmpty())
    }

    @Test
    fun surgeConductorCanBeCastForThreeGenericMana() {
        val d = customScenario(SurgeConductor)
        val p = d.player1
        val conductor = d.putCardInHand(p, "Surge Conductor")
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, conductor).error)
        d.resolveAll()

        assertTrue(d.getPermanents(p).contains(conductor))
        assertEquals(3, d.projected.getPower(conductor))
        assertEquals(2, d.projected.getToughness(conductor))
    }

    @Test
    fun twoGenericManaCannotPayTheThreeManaCost() {
        val d = customScenario(SurgeConductor)
        val p = d.player1
        val conductor = d.putCardInHand(p, "Surge Conductor")
        d.giveColorlessMana(p, 2)

        assertNotNull(d.castSpell(p, conductor).error)

        assertTrue(d.zoneNames(p, Zone.HAND).contains("Surge Conductor"))
        assertFalse(d.permanentsNamed(p, "Surge Conductor").isNotEmpty())
    }

    @Test
    fun itsOwnEntryDoesNotTriggerForAnotherArtifactEntering() {
        val d = customScenario(SurgeConductor, "Sol Ring", "Grizzly Bears")
        val p = d.player1
        d.putPermanentOnBattlefield(p, "Sol Ring")
        val target = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.addComponent(target, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
        val conductor = d.putCardInHand(p, "Surge Conductor")
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, conductor).error)
        d.resolveAll()

        assertTrue(d.getPermanents(p).contains(conductor))
        assertEquals(1, d.counterCount(target, CounterType.PLUS_ONE_PLUS_ONE))
    }

    @Test
    fun controlledNonartifactCreatureEnteringDoesNotProliferate() {
        val d = customScenario(SurgeConductor, "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Surge Conductor")
        val target = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.addComponent(target, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
        val bear = d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.GREEN)
        d.giveColorlessMana(p, 1)

        assertNull(d.castSpell(p, bear).error)
        d.resolveAll(Choices(targets = listOf(target)))

        assertTrue(d.permanentsNamed(p, "Grizzly Bears").size >= 2)
        assertEquals(1, d.counterCount(target, CounterType.PLUS_ONE_PLUS_ONE))
    }

    @Test
    fun artifactTokenEnteringDoesNotProliferate() {
        val d = customScenario(SurgeConductor, "Big Score", "Grizzly Bears")
        val p = d.player1
        d.putCreatureOnBattlefield(p, "Surge Conductor")
        val target = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        d.addComponent(target, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
        val exhibition = d.putCardInHand(p, "Big Score")
        d.putCardInHand(p, "Grizzly Bears")
        d.giveMana(p, Color.RED)
        d.giveColorlessMana(p, 3)

        assertNull(d.castSpell(p, exhibition).error)
        d.resolveAll(Choices(pick = listOf("Grizzly Bears")))

        assertEquals(2, d.permanentsNamed(p, "Treasure").size)
        assertEquals(1, d.counterCount(target, CounterType.PLUS_ONE_PLUS_ONE))
    }

    @Test
    fun artifactEnteringUnderAnOpponentsControlDoesNotProliferate() {
        val d = customScenario(SurgeConductor, "Sol Ring", "Grizzly Bears")
        val p = d.player1
        val opponent = d.player2
        d.putCreatureOnBattlefield(p, "Surge Conductor")
        val target = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.addComponent(target, CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 1)))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = opponent)
        val ring = d.putCardInHand(opponent, "Sol Ring")
        d.giveColorlessMana(opponent, 1)

        assertNull(d.castSpell(opponent, ring).error)
        d.resolveAll(Choices(targets = listOf(target)))

        assertTrue(d.permanentsNamed(opponent, "Sol Ring").isNotEmpty())
        assertEquals(1, d.counterCount(target, CounterType.PLUS_ONE_PLUS_ONE))
    }
}
