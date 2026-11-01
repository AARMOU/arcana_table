package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ThunderhawkGunship
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class ThunderhawkGunshipIndependentCertificationTest {
    @Test
    fun castingRequiresSixGenericManaAndEntersAsAFlyingArtifactVehicle() {
        val d = customScenario(ThunderhawkGunship)
        val player = d.player1
        val gunship = d.putCardInHand(player, "Thunderhawk Gunship")
        d.giveColorlessMana(player, 5)

        assertNotNull(d.castSpell(player, gunship).error)
        assertTrue(d.getHand(player).contains(gunship))
        assertTrue(d.permanentsNamed(player, "Thunderhawk Gunship").isEmpty())

        d.giveColorlessMana(player, 1)
        assertNull(d.castSpell(player, gunship).error)
        d.resolveAll()

        val permanent = d.permanentsNamed(player, "Thunderhawk Gunship").single()
        assertTrue(d.projected.hasType(permanent, "ARTIFACT"))
        assertTrue(d.projected.hasSubtype(permanent, "VEHICLE"))
        assertTrue(d.projected.hasKeyword(permanent, Keyword.FLYING))
        assertFalse(d.projected.isCreature(permanent))
    }

    @Test
    fun enteringCreatesExactlyTwoWhiteVigilantTwoTwoAstartesWarriors() {
        val d = customScenario(ThunderhawkGunship)
        val player = d.player1
        val gunship = d.putCardInHand(player, "Thunderhawk Gunship")
        d.giveColorlessMana(player, 6)

        assertNull(d.castSpell(player, gunship).error)
        assertTrue(d.permanentsNamed(player, "Astartes Warrior").isEmpty())
        d.resolveAll()

        val tokens = d.permanentsNamed(player, "Astartes Warrior")
        assertEquals(2, tokens.size)
        tokens.forEach { token ->
            assertEquals(2, d.projected.getPower(token))
            assertEquals(2, d.projected.getToughness(token))
            assertEquals(1, d.projected.getColors(token).size)
            assertTrue(d.projected.hasColor(token, Color.WHITE))
            assertTrue(d.projected.hasSubtype(token, "ASTARTES"))
            assertTrue(d.projected.hasSubtype(token, "WARRIOR"))
            assertTrue(d.projected.hasKeyword(token, Keyword.VIGILANCE))
        }

        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS, activePlayer = player)
        assertNull(d.declareAttackers(player, listOf(tokens.first()), d.player2).error)
        d.passPriorityUntil(Step.POSTCOMBAT_MAIN)
        assertFalse("A vigilant token stays untapped after attacking", d.isTapped(tokens.first()))
    }

    @Test
    fun crewTwoTapsAControlledCreatureAndMakesTheGunshipACreatureUntilEndOfTurn() {
        val d = customScenario(ThunderhawkGunship, "Grizzly Bears")
        val player = d.player1
        val gunship = d.putPermanentOnBattlefield(player, "Thunderhawk Gunship")
        val bear = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.removeSummoningSickness(bear)

        assertFalse(d.projected.isCreature(gunship))
        assertNull(d.submit(CrewVehicle(player, gunship, listOf(bear))).error)
        d.resolveAll()

        assertTrue(d.isTapped(bear))
        assertFalse(d.isTapped(gunship))
        assertTrue(d.projected.isCreature(gunship))
        assertEquals(6, d.projected.getPower(gunship))
        assertEquals(6, d.projected.getToughness(gunship))
        assertTrue(d.projected.hasKeyword(gunship, Keyword.FLYING))

        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)
        assertFalse("Crew wears off at end of turn", d.projected.isCreature(gunship))
    }

    @Test
    fun crewCannotUseLessThanTwoPowerOrCreaturesControlledByAnOpponent() {
        val d = customScenario(ThunderhawkGunship, "Llanowar Elves", "Grizzly Bears")
        val player = d.player1
        val opponent = d.player2
        val gunship = d.putPermanentOnBattlefield(player, "Thunderhawk Gunship")
        val elf = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        val opposingBear = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.removeSummoningSickness(elf)
        d.removeSummoningSickness(opposingBear)

        assertNotNull(d.submit(CrewVehicle(player, gunship, listOf(elf))).error)
        assertNotNull(d.submit(CrewVehicle(opponent, gunship, listOf(opposingBear))).error)

        assertFalse(d.isTapped(elf))
        assertFalse(d.isTapped(opposingBear))
        assertFalse(d.projected.isCreature(gunship))
    }

    @Test
    fun attackingWithTheGunshipGivesOnlyYourAttackersFlyingUntilEndOfTurn() {
        val d = customScenario(
            ThunderhawkGunship,
            "Grizzly Bears",
            "Llanowar Elves",
            "Colossal Dreadmaw",
        )
        val player = d.player1
        val opponent = d.player2
        val gunship = d.putPermanentOnBattlefield(player, "Thunderhawk Gunship")
        val crew = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        val attacker = d.putCreatureOnBattlefield(player, "Llanowar Elves")
        val bystander = d.putCreatureOnBattlefield(player, "Colossal Dreadmaw")
        val opposingCreature = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        listOf(gunship, crew, attacker, bystander, opposingCreature).forEach(d::removeSummoningSickness)

        assertNull(d.submit(CrewVehicle(player, gunship, listOf(crew))).error)
        d.resolveAll()
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(player, listOf(gunship, attacker), opponent).error)
        d.resolveAll()

        assertTrue(d.projected.hasKeyword(gunship, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(attacker, Keyword.FLYING))
        assertFalse(d.projected.hasKeyword(bystander, Keyword.FLYING))
        assertFalse(d.projected.hasKeyword(opposingCreature, Keyword.FLYING))

        d.passPriorityUntil(Step.DECLARE_BLOCKERS)
        assertNotNull(d.declareBlockers(opponent, mapOf(opposingCreature to listOf(attacker))).error)
        assertNull(d.declareNoBlockers(opponent).error)
        d.passPriorityUntil(Step.UPKEEP, activePlayer = player)
        assertTrue(d.projected.hasKeyword(gunship, Keyword.FLYING))
        assertFalse("The attack-granted flying expires at end of turn",
            d.projected.hasKeyword(attacker, Keyword.FLYING))
    }

    @Test
    fun attackingWithoutTheGunshipDoesNotGrantFlying() {
        val d = customScenario(ThunderhawkGunship, "Grizzly Bears")
        val player = d.player1
        val attacker = d.putCreatureOnBattlefield(player, "Grizzly Bears")
        d.putPermanentOnBattlefield(player, "Thunderhawk Gunship")
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)

        assertNull(d.declareAttackers(player, listOf(attacker), d.player2).error)
        d.resolveAll()

        assertFalse(d.projected.hasKeyword(attacker, Keyword.FLYING))
    }
}
