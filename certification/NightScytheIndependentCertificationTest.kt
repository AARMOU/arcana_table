package com.arcanatable.customcards

import com.arcanatable.customcards.cards.NightScythe
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class NightScytheIndependentCertificationTest {
    @Test
    fun castingCostsThreeGenericAndCreatesANecronWarriorWhenItEnters() {
        val d = customScenario(NightScythe)
        val player = d.player1
        val scythe = d.putCardInHand(player, "Night Scythe")
        d.giveColorlessMana(player, 2)

        assertNotNull(d.castSpell(player, scythe).error)
        assertTrue(d.getHand(player).contains(scythe))
        assertTrue(d.permanentsNamed(player, "Night Scythe").isEmpty())
        assertTrue(d.permanentsNamed(player, "Necron Warrior").isEmpty())

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, scythe).error)
        assertTrue(d.permanentsNamed(player, "Necron Warrior").isEmpty())
        d.resolveAll()

        val vehicle = d.permanentsNamed(player, "Night Scythe").single()
        assertTrue(d.projected.hasType(vehicle, "ARTIFACT"))
        assertTrue(d.projected.hasSubtype(vehicle, "VEHICLE"))
        assertFalse(d.projected.isCreature(vehicle))
        assertTrue(d.projected.hasKeyword(vehicle, Keyword.FLYING))
        assertEquals(3, d.projected.getPower(vehicle))
        assertEquals(1, d.projected.getToughness(vehicle))
        assertEquals(0, d.manaPool(player).colorless)

        val warrior = d.permanentsNamed(player, "Necron Warrior").single()
        assertTrue(d.state.getEntity(warrior)!!.has<TokenComponent>())
        assertTrue(d.projected.hasType(warrior, "ARTIFACT"))
        assertTrue(d.projected.hasType(warrior, "CREATURE"))
        assertTrue(d.projected.hasSubtype(warrior, "NECRON"))
        assertTrue(d.projected.hasSubtype(warrior, "WARRIOR"))
        assertTrue(d.projected.hasColor(warrior, Color.BLACK))
        assertEquals(2, d.projected.getPower(warrior))
        assertEquals(2, d.projected.getToughness(warrior))
    }

    @Test
    fun crewTwoTapsMultipleCreaturesAndEndsAtTheEndOfTurn() {
        val d = customScenario(NightScythe, "Llanowar Elves")
        val player = d.player1
        val vehicle = d.putPermanentOnBattlefield(player, "Night Scythe")
        val firstElf = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        val secondElf = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        listOf(firstElf, secondElf).forEach(d::removeSummoningSickness)

        assertFalse(d.projected.isCreature(vehicle))
        assertNull(d.submit(CrewVehicle(player, vehicle, listOf(firstElf, secondElf))).error)
        d.resolveAll()

        assertTrue(d.isTapped(firstElf))
        assertTrue(d.isTapped(secondElf))
        assertFalse(d.isTapped(vehicle))
        assertTrue(d.projected.hasType(vehicle, "ARTIFACT"))
        assertTrue(d.projected.hasSubtype(vehicle, "VEHICLE"))
        assertTrue(d.projected.isCreature(vehicle))
        assertTrue(d.projected.hasKeyword(vehicle, Keyword.FLYING))
        assertEquals(3, d.projected.getPower(vehicle))
        assertEquals(1, d.projected.getToughness(vehicle))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)

        assertFalse("Crew ends at end of turn", d.projected.isCreature(vehicle))
        assertTrue(d.projected.hasType(vehicle, "ARTIFACT"))
        assertTrue(d.projected.hasSubtype(vehicle, "VEHICLE"))
    }

    @Test
    fun crewCannotUseLessThanTwoPowerOrCreaturesControlledByAnOpponent() {
        val d = customScenario(NightScythe, "Llanowar Elves", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val vehicle = d.putPermanentOnBattlefield(player, "Night Scythe")
        val elf = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        val opposingBear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(elf)
        d.removeSummoningSickness(opposingBear)

        assertNotNull(d.submit(CrewVehicle(player, vehicle, listOf(elf))).error)
        assertNotNull(d.submit(CrewVehicle(opponent, vehicle, listOf(opposingBear))).error)

        assertFalse(d.isTapped(elf))
        assertFalse(d.isTapped(opposingBear))
        assertFalse(d.projected.isCreature(vehicle))
    }

    @Test
    fun aGroundCreatureCannotBlockTheCrewedFlyingVehicle() {
        val d = customScenario(NightScythe, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val vehicle = d.putPermanentOnBattlefield(player, "Night Scythe")
        val crew = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val groundBlocker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        listOf(vehicle, crew).forEach(d::removeSummoningSickness)

        assertNull(d.submit(CrewVehicle(player, vehicle, listOf(crew))).error)
        d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertTrue(d.projected.hasKeyword(vehicle, Keyword.FLYING))
        assertNull(d.declareAttackers(player, listOf(vehicle), opponent).error)
        d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)

        assertNotNull(d.declareBlockers(opponent, mapOf(groundBlocker to listOf(vehicle))).error)
        assertNull(d.declareNoBlockers(opponent).error)
    }
}
