package com.arcanatable.customcards

// Independent scenarios from the supplied Goliath Truck Oracle record.
import com.arcanatable.customcards.cards.GoliathTruck
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId
import org.junit.Assert.*
import org.junit.Test

class GoliathTruckIndependentCertificationTest {
    private val name = "Goliath Truck"

    private fun GameTestDriver.counterCount(permanent: EntityId): Int =
        state.getEntity(permanent)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0

    @Test
    fun castingRequiresFourGenericManaAndEntersAsAFourFourArtifactVehicle() {
        val d = customScenario(GoliathTruck)
        val player = d.player1
        val card = d.putCardInHand(player, name)
        d.giveColorlessMana(player, 3)

        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.getHand(player).contains(card))
        assertTrue(d.permanentsNamed(player, name).isEmpty())

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()

        val truck = d.permanentsNamed(player, name).single()
        assertTrue(d.projected.hasType(truck, "ARTIFACT"))
        assertTrue(d.projected.hasSubtype(truck, "VEHICLE"))
        assertFalse(d.projected.isCreature(truck))
        assertEquals(4, d.projected.getPower(truck))
        assertEquals(4, d.projected.getToughness(truck))
    }

    @Test
    fun crewTwoTapsMultipleControlledCreaturesAndExpiresAtEndOfTurn() {
        val d = customScenario(GoliathTruck, "Llanowar Elves")
        val player = d.player1
        val truck = d.putPermanentOnBattlefield(player, name)
        val firstElf = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        val secondElf = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        listOf(firstElf, secondElf).forEach(d::removeSummoningSickness)

        assertFalse(d.projected.isCreature(truck))
        assertNull(d.submit(CrewVehicle(player, truck, listOf(firstElf, secondElf))).error)
        d.resolveAll()

        assertTrue(d.isTapped(firstElf))
        assertTrue(d.isTapped(secondElf))
        assertFalse(d.isTapped(truck))
        assertTrue(d.projected.isCreature(truck))
        assertEquals(4, d.projected.getPower(truck))
        assertEquals(4, d.projected.getToughness(truck))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)

        assertFalse("Crew ends at end of turn", d.projected.isCreature(truck))
    }

    @Test
    fun oneTwoPowerCreatureCanCrewTheTruckWithoutMana() {
        val d = customScenario(GoliathTruck, "Grizzly Bears")
        val player = d.player1
        val truck = d.putPermanentOnBattlefield(player, name)
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.removeSummoningSickness(bear)

        assertNull(d.submit(CrewVehicle(player, truck, listOf(bear))).error)
        d.resolveAll()

        assertTrue(d.isTapped(bear))
        assertTrue(d.projected.isCreature(truck))
    }

    @Test
    fun onePowerIsNotEnoughToCrewTheTruck() {
        val d = customScenario(GoliathTruck, "Llanowar Elves")
        val player = d.player1
        val truck = d.putPermanentOnBattlefield(player, name)
        val elf = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        d.removeSummoningSickness(elf)

        assertNotNull(d.submit(CrewVehicle(player, truck, listOf(elf))).error)

        assertFalse(d.isTapped(elf))
        assertFalse(d.projected.isCreature(truck))
    }

    @Test
    fun anOpponentCreatureCannotBeTappedToCrewTheTruck() {
        val d = customScenario(GoliathTruck, "Grizzly Bears")
        val player = d.player1
        val truck = d.putPermanentOnBattlefield(player, name)
        val opposingBear = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")
        d.removeSummoningSickness(opposingBear)

        assertNotNull(d.submit(CrewVehicle(player, truck, listOf(opposingBear))).error)

        assertFalse(d.isTapped(opposingBear))
        assertFalse(d.projected.isCreature(truck))
    }

    @Test
    fun attackingWithTheCrewedTruckAddsTwoCountersToAnotherAttackerOnly() {
        val d = customScenario(GoliathTruck, "Grizzly Bears", "Llanowar Elves")
        val player = d.player1
        val opponent = d.player2
        val truck = d.putPermanentOnBattlefield(player, name)
        val crew = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val attacker = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        val bystander = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        listOf(truck, crew, attacker, bystander).forEach(d::removeSummoningSickness)
        assertNull(d.submit(CrewVehicle(player, truck, listOf(crew))).error)
        d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(truck, attacker), opponent).error)
        d.resolveAll(Choices(targets = listOf(attacker)))

        assertEquals(2, d.counterCount(attacker))
        assertEquals(3, d.projected.getPower(attacker))
        assertEquals(3, d.projected.getToughness(attacker))
        assertEquals(0, d.counterCount(bystander))
        assertEquals(0, d.counterCount(truck))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)

        assertEquals("Counters remain after the turn ends", 2, d.counterCount(attacker))
        assertFalse(d.projected.isCreature(truck))
    }

    @Test
    fun attackingWithTheTruckAloneDoesNotPutCountersOnItself() {
        val d = customScenario(GoliathTruck, "Grizzly Bears")
        val player = d.player1
        val truck = d.putPermanentOnBattlefield(player, name)
        val crew = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        listOf(truck, crew).forEach(d::removeSummoningSickness)
        assertNull(d.submit(CrewVehicle(player, truck, listOf(crew))).error)
        d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(truck), d.player2).error)
        d.resolveAll()

        assertEquals(0, d.counterCount(truck))
        assertEquals(4, d.projected.getPower(truck))
        assertEquals(4, d.projected.getToughness(truck))
    }

    @Test
    fun anotherCreatureAttackingWithoutTheTruckDoesNotTriggerStowage() {
        val d = customScenario(GoliathTruck, "Grizzly Bears")
        val player = d.player1
        val truck = d.putPermanentOnBattlefield(player, name)
        val attacker = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(attacker), d.player2).error)
        d.resolveAll()

        assertEquals(0, d.counterCount(attacker))
        assertEquals(0, d.counterCount(truck))
    }
}
