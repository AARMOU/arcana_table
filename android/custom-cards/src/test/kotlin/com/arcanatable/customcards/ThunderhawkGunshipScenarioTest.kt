package com.arcanatable.customcards

import com.arcanatable.customcards.cards.ThunderhawkGunship
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Step
import org.junit.Assert.*
import org.junit.Test

class ThunderhawkGunshipScenarioTest {
    @Test
    fun enteringCreatesTwoWhiteVigilantAstartesWarriorsAndTheVehicleHasFlying() {
        val d = customScenario(ThunderhawkGunship)
        val p = d.player1
        val spell = d.putCardInHand(p, "Thunderhawk Gunship")
        d.giveColorlessMana(p, 6)
        assertNull(d.castSpell(p, spell).error)
        assertTrue(d.getCreatures(p).isEmpty())

        d.resolveAll()

        val vehicle = d.findPermanent(p, "Thunderhawk Gunship")!!
        assertFalse(d.projected.isCreature(vehicle))
        assertTrue(d.projected.hasKeyword(vehicle, Keyword.FLYING))
        val tokens = d.getCreatures(p).filter { d.state.getEntity(it)!!.has<TokenComponent>() }
        assertEquals(2, tokens.size)
        assertEquals(2, d.permanentsNamed(p, "Astartes Warrior").size)
        tokens.forEach { token ->
            assertEquals(2, d.projected.getPower(token))
            assertEquals(2, d.projected.getToughness(token))
            assertTrue(d.projected.hasColor(token, Color.WHITE))
            assertTrue(d.projected.hasSubtype(token, "Astartes"))
            assertTrue(d.projected.hasSubtype(token, "Warrior"))
            assertTrue(d.projected.hasKeyword(token, Keyword.VIGILANCE))
            assertFalse(d.projected.hasKeyword(token, Keyword.FLYING))
        }
        assertTrue(d.getCreatures(d.player2).isEmpty())
    }

    @Test
    fun attackingGrantsFlyingOnlyToYourAttackersUntilEndOfTurn() {
        val d = customScenario(ThunderhawkGunship, "Grizzly Bears")
        val p = d.player1
        val vehicle = d.putPermanentOnBattlefield(p, "Thunderhawk Gunship")
        val crew = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val attacker = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val reserve = d.putCreatureOnBattlefield(p, "Grizzly Bears")
        val opponent = d.putCreatureOnBattlefield(d.player2, "Grizzly Bears")

        assertTrue(d.projected.hasKeyword(vehicle, Keyword.FLYING))
        assertFalse(d.projected.hasKeyword(attacker, Keyword.FLYING))
        assertFalse(d.projected.hasKeyword(reserve, Keyword.FLYING))
        assertNull(d.submit(CrewVehicle(p, vehicle, listOf(crew))).error)
        d.resolveAll()
        assertTrue(d.projected.isCreature(vehicle))

        d.removeSummoningSickness(vehicle)
        d.removeSummoningSickness(attacker)
        d.passPriorityUntil(Step.DECLARE_ATTACKERS)
        assertNull(d.declareAttackers(p, listOf(vehicle, attacker), d.player2).error)
        d.resolveAll()

        assertTrue(d.projected.hasKeyword(vehicle, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(attacker, Keyword.FLYING))
        assertFalse(d.projected.hasKeyword(reserve, Keyword.FLYING))
        assertFalse(d.projected.hasKeyword(opponent, Keyword.FLYING))
        d.passPriorityUntil(Step.PRECOMBAT_MAIN, activePlayer = d.player2)
        assertFalse(d.projected.hasKeyword(attacker, Keyword.FLYING))
        assertTrue(d.projected.hasKeyword(vehicle, Keyword.FLYING))
    }

    @Test
    fun crewTwoRejectsInsufficientPower() {
        val d = customScenario(ThunderhawkGunship, "Ornithopter")
        val p = d.player1
        val vehicle = d.putPermanentOnBattlefield(p, "Thunderhawk Gunship")
        val ornithopter = d.putCreatureOnBattlefield(p, "Ornithopter")

        assertNotNull(d.submit(CrewVehicle(p, vehicle, listOf(ornithopter))).error)
        assertFalse(d.projected.isCreature(vehicle))
        assertFalse(d.isTapped(ornithopter))
    }
}
