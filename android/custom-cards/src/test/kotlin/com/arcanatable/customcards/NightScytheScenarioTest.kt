package com.arcanatable.customcards

import com.arcanatable.customcards.cards.NightScythe
import com.arcanatable.customcards.support.*
import com.wingedsheep.engine.core.CrewVehicle
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import org.junit.Assert.*
import org.junit.Test

class NightScytheScenarioTest {
    @Test
    fun enteringCreatesABlackArtifactNecronWarriorThatCanCrewTheVehicle() {
        val d = customScenario(NightScythe)
        val p = d.player1
        val spell = d.putCardInHand(p, "Night Scythe")
        d.giveColorlessMana(p, 3)
        assertNull(d.castSpell(p, spell).error)
        d.resolveAll()

        val vehicle = d.findPermanent(p, "Night Scythe")!!
        assertFalse(d.projected.isCreature(vehicle))
        assertTrue(d.projected.hasKeyword(vehicle, Keyword.FLYING))
        val token = d.getCreatures(p).single { d.state.getEntity(it)!!.has<TokenComponent>() }
        assertEquals(2, d.projected.getPower(token))
        assertEquals(2, d.projected.getToughness(token))
        assertTrue(d.projected.hasType(token, "ARTIFACT"))
        assertTrue(d.projected.hasColor(token, Color.BLACK))
        assertTrue(d.projected.hasSubtype(token, "Necron"))
        assertTrue(d.projected.hasSubtype(token, "Warrior"))
        assertTrue(d.getCreatures(d.player2).isEmpty())

        // Crew can tap even a creature that entered this turn.
        assertNull(d.submit(CrewVehicle(p, vehicle, listOf(token))).error)
        d.resolveAll()
        assertTrue(d.isTapped(token))
        assertTrue(d.projected.isCreature(vehicle))
        assertEquals(3, d.projected.getPower(vehicle))
        assertEquals(1, d.projected.getToughness(vehicle))
        assertTrue(d.projected.hasKeyword(vehicle, Keyword.FLYING))
    }

    @Test
    fun crewTwoRejectsInsufficientPower() {
        val d = customScenario(NightScythe, "Ornithopter")
        val p = d.player1
        val vehicle = d.putPermanentOnBattlefield(p, "Night Scythe")
        val ornithopter = d.putCreatureOnBattlefield(p, "Ornithopter")
        assertNotNull(d.submit(CrewVehicle(p, vehicle, listOf(ornithopter))).error)
        assertFalse(d.projected.isCreature(vehicle))
        assertFalse(d.isTapped(ornithopter))
    }
}
