package com.arcanatable.customcards

import com.arcanatable.customcards.cards.KnightPaladin
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class KnightPaladinIndependentCertificationTest {
    @Test
    fun castingRequiresFiveGenericManaAndEntersAsAnArtifactVehicleSixSix() {
        val d = customScenario(KnightPaladin)
        val player = d.player1
        val card = d.putCardInHand(player, "Knight Paladin")
        d.giveColorlessMana(player, 4)

        assertNotNull(d.castSpell(player, card).error)
        assertTrue(d.getHand(player).contains(card))
        assertTrue(d.permanentsNamed(player, "Knight Paladin").isEmpty())

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, card).error)
        d.resolveAll()

        val knight = d.permanentsNamed(player, "Knight Paladin").single()
        assertTrue(d.projected.hasType(knight, "ARTIFACT"))
        assertTrue(d.projected.hasSubtype(knight, "VEHICLE"))
        assertFalse(d.projected.isCreature(knight))
        assertEquals(6, d.projected.getPower(knight))
        assertEquals(6, d.projected.getToughness(knight))
    }

    @Test
    fun enteringDealsFourDamageToEachOpponentButNotItsController() {
        val d = customScenario(KnightPaladin)
        val player = d.player1
        val opponent = d.player2
        val card = d.putCardInHand(player, "Knight Paladin")
        d.giveColorlessMana(player, 5)

        assertNull(d.castSpell(player, card).error)
        assertEquals(20, d.getLifeTotal(player))
        assertEquals(20, d.getLifeTotal(opponent))
        d.resolveAll()

        assertEquals(20, d.getLifeTotal(player))
        assertEquals(16, d.getLifeTotal(opponent))
        assertEquals(1, d.permanentsNamed(player, "Knight Paladin").size)
    }

    @Test
    fun crewOneTapsAControlledOnePowerCreatureAndEndsAtEndOfTurn() {
        val d = customScenario(KnightPaladin, "Llanowar Elves")
        val player = d.player1
        val knight = d.putPermanentOnBattlefield(player, "Knight Paladin")
        val elf = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        assertEquals(1, d.projected.getPower(elf))
        assertFalse(d.projected.isCreature(knight))

        assertNull(d.submit(CrewVehicle(player, knight, listOf(elf))).error)
        d.resolveAll()

        assertTrue(d.isTapped(elf))
        assertFalse(d.isTapped(knight))
        assertTrue(d.projected.isCreature(knight))
        assertTrue(d.projected.hasType(knight, "ARTIFACT"))
        assertEquals(6, d.projected.getPower(knight))
        assertEquals(6, d.projected.getToughness(knight))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)

        assertFalse(d.projected.isCreature(knight))
    }

    @Test
    fun crewRejectsZeroPowerTappedAndOpponentsCreatures() {
        val d = customScenario(KnightPaladin, "Wall of Omens", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val knight = d.putPermanentOnBattlefield(player, "Knight Paladin")
        val zeroPowerCreature = d.putCreatureOnBattlefield(player, "Wall of Omens")
        val tappedCreature = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val opposingCreature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.tapPermanent(tappedCreature)
        assertEquals(0, d.projected.getPower(zeroPowerCreature))

        assertNotNull(d.submit(CrewVehicle(player, knight, listOf(zeroPowerCreature))).error)
        assertNotNull(d.submit(CrewVehicle(player, knight, listOf(tappedCreature))).error)
        assertNotNull(d.submit(CrewVehicle(opponent, knight, listOf(opposingCreature))).error)

        assertFalse(d.isTapped(zeroPowerCreature))
        assertTrue(d.isTapped(tappedCreature))
        assertFalse(d.isTapped(opposingCreature))
        assertFalse(d.projected.isCreature(knight))
    }

    @Test
    fun trampleAssignsLethalDamageToABlockerAndExcessToTheDefendingPlayer() {
        val d = customScenario(KnightPaladin, "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val knight = d.putPermanentOnBattlefield(player, "Knight Paladin")
        val crew = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val blocker = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(knight)

        assertTrue(d.projected.hasKeyword(knight, Keyword.TRAMPLE))
        assertNull(d.submit(CrewVehicle(player, knight, listOf(crew))).error)
        d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(knight), opponent).error)
        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNull(d.declareBlockers(opponent, mapOf(blocker to listOf(knight))).error)
        d.passPriorityUntil(Step.COMBAT_DAMAGE)
        assertNull(d.submitCombatDamage(mapOf((knight to blocker) to 2, (knight to opponent) to 4)).error)
        d.resolveAll()

        assertEquals(16, d.getLifeTotal(opponent))
        assertTrue(d.getGraveyardCardNames(opponent).contains("Grizzly Bears"))
    }
}
